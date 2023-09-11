package com.atlassian.performance.tools.awsinfrastructure.api.jira

import com.amazonaws.services.cloudformation.model.Parameter
import com.amazonaws.services.ec2.model.Filter
import com.amazonaws.services.ec2.model.Tag
import com.atlassian.performance.tools.aws.api.*
import com.atlassian.performance.tools.awsinfrastructure.TemplateBuilder
import com.atlassian.performance.tools.awsinfrastructure.api.hardware.C4EightExtraLargeElastic
import com.atlassian.performance.tools.awsinfrastructure.api.hardware.Computer
import com.atlassian.performance.tools.awsinfrastructure.api.hardware.M4ExtraLargeElastic
import com.atlassian.performance.tools.awsinfrastructure.api.hardware.Volume
import com.atlassian.performance.tools.awsinfrastructure.api.loadbalancer.LoadBalancerFormula
import com.atlassian.performance.tools.awsinfrastructure.api.network.Network
import com.atlassian.performance.tools.awsinfrastructure.api.network.NetworkFormula
import com.atlassian.performance.tools.awsinfrastructure.api.network.ProvisionedNetwork
import com.atlassian.performance.tools.awsinfrastructure.api.network.access.ForIpAccessRequester
import com.atlassian.performance.tools.awsinfrastructure.api.network.access.LocalPublicIpv4Provider
import com.atlassian.performance.tools.awsinfrastructure.aws.TokenScrollingEc2
import com.atlassian.performance.tools.infrastructure.api.jira.JiraNodeConfig
import com.atlassian.performance.tools.infrastructure.api.jira.install.HttpNode
import com.atlassian.performance.tools.infrastructure.api.jira.install.TcpNode
import com.atlassian.performance.tools.infrastructure.api.jira.start.hook.PreStartHooks
import com.atlassian.performance.tools.infrastructure.api.loadbalancer.LoadBalancer
import com.atlassian.performance.tools.infrastructure.api.loadbalancer.LoadBalancerPlan
import com.atlassian.performance.tools.infrastructure.api.network.HttpServerRoom
import com.atlassian.performance.tools.infrastructure.api.network.Networked
import com.atlassian.performance.tools.infrastructure.api.network.TcpServerRoom
import com.atlassian.performance.tools.ssh.api.Ssh
import com.atlassian.performance.tools.ssh.api.SshHost
import com.atlassian.performance.tools.workspace.api.RootWorkspace
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.nio.file.Path
import java.time.Duration
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.function.Supplier

class LegacyAwsInfrastructure private constructor(
    private val aws: Aws,
    private val investment: Investment,
    private val networking: Supplier<ProvisionedNetwork>,
    private val workspace: Path,
    private val jiraComputer: Computer,
    private val jiraVolume: Volume,
    private val jiraNodeConfigs: List<JiraNodeConfig>,
    private val databaseComputer: Computer,
    private val databaseVolume: Volume,
    private val provisioningTimout: Duration
) : Networked, AutoCloseable {
    private val logger: Logger = LogManager.getLogger(this::class.java)
    private val nonce = UUID.randomUUID().toString()
    private val sshKey: SshKey by lazy { provisionKey() }
    private val provisionedNetwork: ProvisionedNetwork by lazy { networking.get() }
    private val network: Network by lazy { provisionedNetwork.network }
    private val provisioning: ProvisionedStack by lazy { provisionStack() }
    private val deprovisioning: CompletableFuture<*> by lazy {
        provisioning.release()
        provisionedNetwork.resource.release()
        sshKey.remote.release()
    }

    private fun provisionKey(): SshKey {
        return SshKeyFormula(aws.ec2, workspace, nonce, investment.lifespan).provision()
    }

    private fun provisionStack(): ProvisionedStack {
        logger.info("Setting up Jira stack...")
        val template = TemplateBuilder("2-nodes-dc-hooks.yaml").adaptTo(jiraNodeConfigs)
        val stack = StackFormula(
            investment = investment,
            cloudformationTemplate = template,
            parameters = listOf(
                Parameter()
                    .withParameterKey("KeyName")
                    .withParameterValue(sshKey.remote.name),
                Parameter()
                    .withParameterKey("InstanceProfile")
                    .withParameterValue(aws.shortTermStorageAccess()),
                Parameter()
                    .withParameterKey("Ami")
                    .withParameterValue(aws.defaultAmi),
                Parameter()
                    .withParameterKey("JiraInstanceType")
                    .withParameterValue(jiraComputer.instanceType.toString()),
                Parameter()
                    .withParameterKey("JiraVolumeSize")
                    .withParameterValue(jiraVolume.size.toString()),
                Parameter()
                    .withParameterKey("DatabaseInstanceType")
                    .withParameterValue(databaseComputer.instanceType.toString()),
                Parameter()
                    .withParameterKey("DatabaseVolumeSize")
                    .withParameterValue(databaseVolume.size.toString()),
                Parameter()
                    .withParameterKey("Vpc")
                    .withParameterValue(network.vpc.vpcId),
                Parameter()
                    .withParameterKey("Subnet")
                    .withParameterValue(network.subnet.subnetId),
                Parameter()
                    .withParameterKey("AccessCidr")
                    .withParameterValue(network.vpc.cidrBlock)
            ),
            aws = aws,
            pollingTimeout = provisioningTimout
        ).provision()
        logger.info("Jira stack is provisioned, it will expire at ${stack.expiry}")
        return stack
    }

    override fun close() {
        deprovisioning.get()
    }

    override fun subnet(): String = network.subnet.cidrBlock

    val jiraNodesServerRoom: HttpServerRoom = StackJiraNodes()
    val databaseServerRoom: TcpServerRoom = StackDatabase()
    val sharedHomeServerRoom: TcpServerRoom = StackSharedHome()

    private fun listMachines() = provisioning.listMachines()

    fun balance(formula: LoadBalancerFormula): LoadBalancerPlan {
        return object : LoadBalancerPlan {
            override fun materialize(nodes: List<HttpNode>, hooks: List<PreStartHooks>): LoadBalancer {
                val filter = Filter(
                    "network-interface.addresses.private-ip-address",
                    nodes.map { it.tcp.privateIp }
                )
                val instances = TokenScrollingEc2(aws.ec2).findInstances(filter)
                return formula
                    .provision(
                        investment,
                        instances,
                        network.vpc,
                        network.subnet,
                        sshKey,
                        aws
                    )
                    .also { it.accessProvider.provideAccess("0.0.0.0/0") }
                    .also { ForIpAccessRequester(LocalPublicIpv4Provider.Builder().build()).requestAccess(it.accessProvider) }
                    .loadBalancer
            }
        }
    }

    private inner class StackSharedHome : TcpServerRoom {

        override fun serveTcp(name: String): TcpNode {
            val machine = listMachines().single { it.tags.contains(Tag("jpt-shared-home", "true")) }
            val publicIp = machine.publicIpAddress
            val ssh = Ssh(SshHost(publicIp, "ubuntu", sshKey.file.path), connectivityPatience = 4)
            sshKey.file.facilitateSsh(publicIp)
            ssh.newConnection().use { jiraComputer.setUp(it) }
            return TcpNode(
                publicIp,
                machine.privateIpAddress,
                3306,
                name,
                ssh
            )
        }

        override fun serveTcp(name: String, tcpPorts: List<Int>, udpPorts: List<Int>): TcpNode {
            val ports = "TCP $tcpPorts and UDP $udpPorts"
            throw Exception(
                "It's unclear whether $ports are expected to be open to the public or privately." +
                    " All ports are open within the VPC."
            )
        }
    }

    private inner class StackDatabase : TcpServerRoom {

        override fun serveTcp(name: String, tcpPorts: List<Int>, udpPorts: List<Int>): TcpNode {
            if (tcpPorts.singleOrNull() == 3306 && udpPorts.isEmpty()) {
                return serveTcp(name)
            } else {
                throw Exception("The stack is not prepared for TCP $tcpPorts and UDP $udpPorts")
            }
        }

        override fun serveTcp(name: String): TcpNode {
            val machine = listMachines().single { it.tags.contains(Tag("jpt-database", "true")) }
            val publicIp = machine.publicIpAddress
            val ssh = Ssh(SshHost(publicIp, "ubuntu", sshKey.file.path), connectivityPatience = 5)
            sshKey.file.facilitateSsh(publicIp)
            ssh.newConnection().use { databaseComputer.setUp(it) }
            return TcpNode(
                publicIp,
                machine.privateIpAddress,
                3306,
                name,
                ssh
            )
        }
    }

    private inner class StackJiraNodes : HttpServerRoom {

        private val machines by lazy {
            listMachines().filter { it.tags.contains(Tag("jpt-jira", "true")) }
        }
        private var nodesRequested = 0

        override fun serveHttp(name: String): HttpNode {
            val machine =
                machines[nodesRequested++] // TODO looks like a yikes, relies on sync across `List<JiraNodeConfig>` and `List<JiraNodePlan>`
            val publicIp = machine.publicIpAddress
            val ssh = Ssh(SshHost(publicIp, "ubuntu", sshKey.file.path), connectivityPatience = 5)
            sshKey.file.facilitateSsh(publicIp)
            ssh.newConnection().use { jiraComputer.setUp(it) }
            val tcp = TcpNode(
                publicIp,
                machine.privateIpAddress,
                8080,
                name,
                ssh
            )
            return HttpNode(tcp, "/", false)
        }
    }

    class Builder(
        private var aws: Aws,
        private var investment: Investment
    ) {
        private var networking: Supplier<ProvisionedNetwork> =
            Supplier { NetworkFormula(investment, aws).provisionAsResource() }
        private var jiraNodeConfigs: List<JiraNodeConfig> = listOf(1, 2).map { JiraNodeConfig.Builder().build() }
        private var jiraComputer: Computer = C4EightExtraLargeElastic()
        private var jiraVolume: Volume = Volume(100)
        private var databaseComputer: Computer = M4ExtraLargeElastic()
        private var databaseVolume: Volume = Volume(100)
        private var provisioningTimout: Duration = Duration.ofMinutes(30)
        private var workspace: Path = RootWorkspace().currentTask.isolateTest(investment.reuseKey()).directory

        fun aws(aws: Aws) = apply { this.aws = aws }
        fun networking(networking: Supplier<ProvisionedNetwork>) = apply { this.networking = networking }
        fun investment(investment: Investment) = apply { this.investment = investment }
        fun jiraNodeConfigs(jiraNodeConfigs: List<JiraNodeConfig>) =
            apply { this.jiraNodeConfigs = jiraNodeConfigs }

        fun jiraComputer(jiraComputer: Computer) = apply { this.jiraComputer = jiraComputer }
        fun jiraVolume(jiraVolume: Volume) = apply { this.jiraVolume = jiraVolume }
        fun databaseComputer(databaseComputer: Computer) = apply { this.databaseComputer = databaseComputer }
        fun databaseVolume(databaseVolume: Volume) = apply { this.databaseVolume = databaseVolume }
        fun provisioningTimout(provisioningTimout: Duration) =
            apply { this.provisioningTimout = provisioningTimout }

        fun workspace(workspace: Path) = apply { this.workspace = workspace }

        fun build(): LegacyAwsInfrastructure = LegacyAwsInfrastructure(
            aws = aws,
            networking = networking,
            investment = investment,
            jiraNodeConfigs = jiraNodeConfigs,
            jiraComputer = jiraComputer,
            jiraVolume = jiraVolume,
            databaseComputer = databaseComputer,
            databaseVolume = databaseVolume,
            provisioningTimout = provisioningTimout,
            workspace = workspace
        )
    }
}

