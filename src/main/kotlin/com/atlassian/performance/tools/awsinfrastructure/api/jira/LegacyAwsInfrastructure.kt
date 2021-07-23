package com.atlassian.performance.tools.awsinfrastructure.api.jira

import com.amazonaws.services.cloudformation.model.Parameter
import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.model.*
import com.amazonaws.services.ec2.model.Tag
import com.atlassian.performance.tools.aws.api.*
import com.atlassian.performance.tools.awsinfrastructure.TemplateBuilder
import com.atlassian.performance.tools.awsinfrastructure.api.Network
import com.atlassian.performance.tools.awsinfrastructure.api.hardware.C4EightExtraLargeElastic
import com.atlassian.performance.tools.awsinfrastructure.api.hardware.Computer
import com.atlassian.performance.tools.awsinfrastructure.api.hardware.M4ExtraLargeElastic
import com.atlassian.performance.tools.awsinfrastructure.api.hardware.Volume
import com.atlassian.performance.tools.infrastructure.api.jira.JiraNodeConfig
import com.atlassian.performance.tools.infrastructure.api.jira.install.HttpNode
import com.atlassian.performance.tools.infrastructure.api.jira.install.TcpNode
import com.atlassian.performance.tools.infrastructure.api.network.HttpServerRoom
import com.atlassian.performance.tools.infrastructure.api.network.Networked
import com.atlassian.performance.tools.infrastructure.api.network.SshServerRoom
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
import java.util.concurrent.ConcurrentLinkedQueue

class LegacyAwsInfrastructure private constructor(
    private val aws: Aws,
    private val network: Network,
    private val investment: Investment,
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
    private val provisioning: ProvisionedStack by lazy { provisionStack() }
    private val deprovisioning: CompletableFuture<*> by lazy { provisioning.release() }

    private fun provisionKey(): SshKey {
        return SshKeyFormula(aws.ec2, workspace, nonce, investment.lifespan).provision()
    }

    private fun provisionStack(): ProvisionedStack {
        logger.info("Setting up Jira stack...")
        val template = TemplateBuilder("2-nodes-dc.yaml").adaptTo(jiraNodeConfigs)
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
                    .withParameterValue(network.subnet.subnetId)
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

    private fun listMachines() = provisioning.listMachines()

    fun forDatabase(): TcpServerRoom {
        return StackDatabase()
    }

    fun forSharedHome(): SshServerRoom {
        return StackSharedHome()
    }

    fun forJiraNodes(): HttpServerRoom {
        return StackJiraNodes()
    }

    fun forLoadBalancer(): HttpServerRoom {
        return Ec2ServerRoom()
    }

    private inner class StackSharedHome : SshServerRoom {

        override fun serveSsh(name: String): Ssh {
            val machine = listMachines().single { it.tags.contains(Tag("jpt-shared-home", "true")) }
            val publicIp = machine.publicIpAddress
            val ssh = Ssh(SshHost(publicIp, "ubuntu", sshKey.file.path), connectivityPatience = 4)
            sshKey.file.facilitateSsh(publicIp)
            ssh.newConnection().use { jiraComputer.setUp(it) }
            return ssh
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
                3306,
                name,
                ssh
            )
            return HttpNode(tcp, "/", false)
        }
    }

    private inner class Ec2ServerRoom : HttpServerRoom {

        private val balancerPort = 80
        private val resources = ConcurrentLinkedQueue<Resource>()

        override fun serveHttp(name: String): HttpNode {
            val httpAccess = httpAccess(investment, aws.ec2, aws.awaitingEc2, network.vpc)
            val (ssh, resource) = aws.awaitingEc2.allocateInstance(
                investment = investment,
                key = sshKey,
                vpcId = network.vpc.vpcId,
                customizeLaunch = { launch ->
                    launch
                        .withSecurityGroupIds(httpAccess.groupId)
                        .withSubnetId(network.subnet.subnetId)
                        .withInstanceType(InstanceType.M5Large)
                }
            )
            resources += resource
            sshKey.file.facilitateSsh(ssh.host.ipAddress)
            return HttpNode(
                TcpNode(

                ),
                "/",
                false
            )
        }

        private fun httpAccess(
            investment: Investment,
            ec2: AmazonEC2,
            awaitingEc2: AwaitingEc2,
            vpc: Vpc
        ): SecurityGroup {
            val securityGroup = awaitingEc2.allocateSecurityGroup(
                investment,
                CreateSecurityGroupRequest()
                    .withGroupName("${investment.reuseKey()}-HttpListener")
                    .withDescription("Enables HTTP access")
                    .withVpcId(vpc.vpcId)
            )
            ec2.authorizeSecurityGroupIngress(
                AuthorizeSecurityGroupIngressRequest()
                    .withGroupId(securityGroup.groupId)
                    .withIpPermissions(
                        IpPermission()
                            .withIpProtocol("tcp")
                            .withFromPort(balancerPort)
                            .withToPort(balancerPort)
                            .withIpv4Ranges(
                                IpRange().withCidrIp("0.0.0.0/0")
                            )
                    )
            )
            return securityGroup
        }
    }

    class Builder(
        private var aws: Aws,
        private var network: Network,
        private var investment: Investment
    ) {
        private var jiraNodeConfigs: List<JiraNodeConfig> = listOf(1, 2).map { JiraNodeConfig.Builder().build() }
        private var jiraComputer: Computer = C4EightExtraLargeElastic()
        private var jiraVolume: Volume = Volume(100)
        private var databaseComputer: Computer = M4ExtraLargeElastic()
        private var databaseVolume: Volume = Volume(100)
        private var provisioningTimout: Duration = Duration.ofMinutes(30)
        private var workspace: Path = RootWorkspace().currentTask.isolateTest(investment.reuseKey()).directory

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
            network = network,
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
