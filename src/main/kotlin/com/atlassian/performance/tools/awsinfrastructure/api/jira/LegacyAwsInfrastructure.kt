package com.atlassian.performance.tools.awsinfrastructure.api.jira

import com.amazonaws.services.cloudformation.model.Parameter
import com.amazonaws.services.ec2.model.*
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
import com.atlassian.performance.tools.awsinfrastructure.api.network.access.*
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
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.function.Supplier

/**
 * @param [networking] all machines in this infra have access to the VPC CIDR
 */
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
    private val resources: Queue<Resource> = ConcurrentLinkedQueue()
    private val sshKey: SshKey by lazy { provisionKey() }
    private val network: Network by lazy { provisionNetwork() }
    private val stack: ProvisionedStack by lazy { provisionStack() }

    private fun provisionKey(): SshKey {
        val key = SshKeyFormula(aws.ec2, workspace, nonce, investment.lifespan).provision()
        resources.add(key.remote)
        return key
    }

    private fun provisionNetwork(): Network {
        val provisionedNetwork = networking.get()
        resources.add(provisionedNetwork.resource)
        return provisionedNetwork.network
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
        resources.add(stack)
        logger.info("Jira stack is provisioned, it will expire at ${stack.expiry}")
        return stack
    }

    override fun close() {
        CompositeResource(resources.toList()).release().get()
    }

    override fun subnet(): String = network.subnet.cidrBlock

    val jiraNodesServerRoom: HttpServerRoom = StackJiraNodes()
    val databaseServerRoom: TcpServerRoom = StackDatabase()
    val sharedHomeServerRoom: TcpServerRoom = StackSharedHome()
    val balancerServerRoom: HttpServerRoom = Ec2Balancer()

    private fun listMachines() = stack.listMachines()

    private inner class Ec2Balancer : HttpServerRoom {

        private val port = 80

        override fun serveHttp(name: String): HttpNode {
            logger.info("Setting up Apache load balancer...")
            val ec2 = aws.ec2
            val securityGroup = aws.awaitingEc2.allocateSecurityGroup(
                investment,
                CreateSecurityGroupRequest()
                    .withGroupName("${investment.reuseKey()}-HttpListener")
                    .withDescription("Load balancer security group")
                    .withVpcId(network.vpc.vpcId)
            )
            val (ssh, resource, instance) = aws.awaitingEc2.allocateInstance(
                investment = investment,
                key = sshKey,
                vpcId = network.vpc.vpcId,
                customizeLaunch = { launch ->
                    launch
                        .withInstanceInitiatedShutdownBehavior(ShutdownBehavior.Terminate)
                        .withSecurityGroupIds(securityGroup.groupId)
                        .withSubnetId(network.subnet.subnetId)
                        .withInstanceType(InstanceType.M5Large)
                        .withIamInstanceProfile(IamInstanceProfileSpecification().withName(aws.shortTermStorageAccess()))
                }
            )
            resources.add(
                DependentResources(
                    user = resource,
                    dependency = Ec2SecurityGroup(securityGroup, ec2)
                )
            )
            sshKey.file.facilitateSsh(ssh.host.ipAddress)
            val accessToBalancer = SecurityGroupIngressAccessProvider
                .Builder(ec2 = aws.ec2, securityGroup = securityGroup, portRange = port..port)
                .build()
            grantAccessFromVpc(accessToBalancer)
            grantSelfAccess(accessToBalancer, instance)
            grantAccessFromLocal(accessToBalancer)
            val tcp = TcpNode(
                publicIp = instance.publicIpAddress,
                privateIp = instance.privateIpAddress,
                port = port,
                name = name,
                ssh = ssh
            )
            return HttpNode(
                tcp = tcp,
                basePath = "/",
                supportsTls = false
            )
        }

        private fun grantAccessFromVpc(accessToBalancer: AccessProvider) {
            accessToBalancer.provideAccess(network.vpc.cidrBlock)
        }

        /**
         * This was missed when reporting and fixing JPERF-790.
         *
         * For Jira pre 8.9.0 if the instance has no access to its own HTTP the dashboard view may freeze
         * (in our case it was after log in, however it may be related to dataset config).
         *
         * This access to self is described as required in the [setup documentation](https://confluence.atlassian.com/jirakb/configure-linux-firewall-for-jira-applications-741933610.html)
         * and it was missed in implementation of aws-infrastructure 2.24.0
         *
         * > 4 - Allowing connections to JIRA from itself (to ensure you don't run into problems with
         * > [gadget titles showing as __MSG_gadget](https://confluence.atlassian.com/jirakb/fix-gadget-titles-showing-as-__msg_gadget-in-jira-server-813697086.html))
         *
         * > ```iptables -t nat -I OUTPUT -p tcp -o lo --dport 80 -j REDIRECT --to-ports 8080```
         */
        private fun grantSelfAccess(accessToBalancer: AccessProvider, instance: Instance) {
            accessToBalancer.provideAccess(instance.publicIpAddress.ipToCidr())
        }

        /**
         * Grant access from local laptop to facilitate investigations when provisioning goes wrong.
         */
        private fun grantAccessFromLocal(accessToBalancer: AccessProvider) {
            val localIp = LocalPublicIpv4Provider.Builder().build().get()
            accessToBalancer.provideAccess(localIp.ipToCidr())
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

