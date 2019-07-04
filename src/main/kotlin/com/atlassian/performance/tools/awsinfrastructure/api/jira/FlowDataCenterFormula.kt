package com.atlassian.performance.tools.awsinfrastructure.api.jira

import com.amazonaws.services.cloudformation.model.Parameter
import com.amazonaws.services.ec2.model.Instance
import com.amazonaws.services.ec2.model.Tag
import com.atlassian.performance.tools.aws.api.*
import com.atlassian.performance.tools.awsinfrastructure.NetworkFormula
import com.atlassian.performance.tools.awsinfrastructure.TemplateBuilder
import com.atlassian.performance.tools.awsinfrastructure.api.Network
import com.atlassian.performance.tools.awsinfrastructure.api.hardware.C4EightExtraLargeElastic
import com.atlassian.performance.tools.awsinfrastructure.api.hardware.Computer
import com.atlassian.performance.tools.awsinfrastructure.api.hardware.Volume
import com.atlassian.performance.tools.awsinfrastructure.api.loadbalancer.ApacheEc2LoadBalancerFormula
import com.atlassian.performance.tools.awsinfrastructure.api.loadbalancer.ApacheProxyLoadBalancer
import com.atlassian.performance.tools.awsinfrastructure.api.loadbalancer.LoadBalancerFormula
import com.atlassian.performance.tools.awsinfrastructure.api.loadbalancer.ProvisionedLoadBalancer
import com.atlassian.performance.tools.awsinfrastructure.jira.home.SharedHomeFormula
import com.atlassian.performance.tools.awsinfrastructure.jira.home.SharedHomeHook
import com.atlassian.performance.tools.awsinfrastructure.loadbalancer.ApacheProxyFix
import com.atlassian.performance.tools.concurrency.api.submitWithLogContext
import com.atlassian.performance.tools.infrastructure.api.jira.EmptyJiraHome
import com.atlassian.performance.tools.infrastructure.api.jira.JiraHomeSource
import com.atlassian.performance.tools.infrastructure.api.jira.SharedHome
import com.atlassian.performance.tools.infrastructure.api.jira.flow.TcpServer
import com.atlassian.performance.tools.infrastructure.api.jira.flow.install.InstalledJira
import com.atlassian.performance.tools.infrastructure.api.jira.flow.server.StartedJira
import com.atlassian.performance.tools.infrastructure.api.loadbalancer.LoadBalancer
import com.atlassian.performance.tools.jvmtasks.api.TaskTimer.time
import com.atlassian.performance.tools.ssh.api.Ssh
import com.atlassian.performance.tools.ssh.api.SshHost
import org.apache.logging.log4j.CloseableThreadContext
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future

class FlowDataCenterFormula private constructor(
    private val nodes: List<JiraNodeProvisioning>,
    private val loadBalancerFormula: LoadBalancerFormula,
    private val sharedHomeSource: JiraHomeSource,
    private val computer: Computer,
    private val volume: Volume,
    private val stackPatience: Duration,
    private val overriddenNetwork: Network? = null
) : JiraFormula {
    private val logger: Logger = LogManager.getLogger(this::class.java)

    override fun provision(
        investment: Investment,
        pluginsTransport: Storage,
        resultsTransport: Storage,
        key: Future<SshKey>,
        roleProfile: String,
        aws: Aws
    ): ProvisionedJira = time("provision Jira Data Center") {
        logger.info("Setting up Jira DC...")
        val network = overriddenNetwork ?: NetworkFormula(investment, aws).provision()
        val sshKey = key.get()
        val stack = provisionStack(investment, sshKey, roleProfile, aws, network)
        val machines = stack.listMachines()
        val jiraNodes = machines.filter { it.tags.contains(Tag("jpt-jira", "true")) }
        val executor = Executors.newCachedThreadPool { runnable ->
            Thread(runnable, "dc-provisioning-${runnable.hashCode()}")
        }
        val futureSharedHome = provisionSharedHome(machines, sshKey, pluginsTransport, executor)
        val futureLoadBalancer = provisionLoadBalancer(investment, jiraNodes, network, sshKey, aws, executor)
        nodes.forEach { it.flow.hookPostInstall(SharedHomeHook(futureSharedHome.get())) }
        val provisionedLoadBalancer = futureLoadBalancer.get()
        val loadBalancer = provisionedLoadBalancer.loadBalancer
        hackInTheLbHooks(loadBalancer)
        val futureInstalledNodes = installNodes(jiraNodes, sshKey.file, executor)
        val startedNodes = startNodes(futureInstalledNodes, sshKey.file.path)
        executor.shutdownNow()
        time("wait for loadbalancer") {
            loadBalancer.waitUntilHealthy(Duration.ofMinutes(5))
        }
        val jira = minimumFeatures(loadBalancer.uri)
        logger.info("$jira is set up, will expire ${stack.expiry}")
        return@time ProvisionedJira(
            jira = jira,
            resource = DependentResources(
                user = provisionedLoadBalancer.resource,
                dependency = stack
            )
        )
    }

    private fun provisionStack(
        investment: Investment,
        key: SshKey,
        roleProfile: String,
        aws: Aws,
        network: Network
    ): ProvisionedStack = time("provision stack") {
        StackFormula(
            investment = investment,
            cloudformationTemplate = TemplateBuilder("2-nodes-dc.yaml").build(), // TODO.adaptTo(configs),
            parameters = listOf(
                Parameter()
                    .withParameterKey("KeyName")
                    .withParameterValue(key.remote.name),
                Parameter()
                    .withParameterKey("InstanceProfile")
                    .withParameterValue(roleProfile),
                Parameter()
                    .withParameterKey("Ami")
                    .withParameterValue(aws.defaultAmi),
                Parameter()
                    .withParameterKey("JiraInstanceType")
                    .withParameterValue(computer.instanceType.toString()),
                Parameter()
                    .withParameterKey("JiraVolumeSize")
                    .withParameterValue(volume.size.toString()),
                Parameter()
                    .withParameterKey("Vpc")
                    .withParameterValue(network.vpc.vpcId),
                Parameter()
                    .withParameterKey("Subnet")
                    .withParameterValue(network.subnet.subnetId)
            ),
            aws = aws,
            pollingTimeout = stackPatience
        ).provision()
    }

    private fun provisionLoadBalancer(
        investment: Investment,
        jiraNodes: List<Instance>,
        network: Network,
        sshKey: SshKey,
        aws: Aws,
        executor: ExecutorService
    ): CompletableFuture<ProvisionedLoadBalancer> = executor.submitWithLogContext("provision load balancer") {
        loadBalancerFormula.provision(
            investment = investment,
            instances = jiraNodes,
            subnet = network.subnet,
            vpc = network.vpc,
            key = sshKey,
            aws = aws
        )
    }

    private fun hackInTheLbHooks(loadBalancer: LoadBalancer) {
        if (loadBalancer is ApacheProxyLoadBalancer) {
            nodes.forEach { it.flow.hookPreStart(ApacheProxyFix(loadBalancer.uri)) }
        }
    }

    private fun provisionSharedHome(
        machines: List<Instance>,
        sshKey: SshKey,
        pluginsTransport: Storage,
        executor: ExecutorService
    ): CompletableFuture<SharedHome> = executor.submitWithLogContext("provision shared home") {
        val instance = machines.single { it.tags.contains(Tag("jpt-shared-home", "true")) }
        val publicIp = instance.publicIpAddress
        logger.info("Setting up shared home...")
        sshKey.file.facilitateSsh(publicIp)
        val sharedHome = SharedHomeFormula(
            jiraHomeSource = sharedHomeSource,
            pluginsTransport = pluginsTransport,
            ip = instance.privateIpAddress,
            ssh = Ssh(
                host = SshHost(publicIp, "ubuntu", sshKey.file.path),
                connectivityPatience = 4
            ),
            computer = computer
        ).provision()
        logger.info("Shared home is set up")
        return@submitWithLogContext sharedHome
    }

    private fun installNodes(
        jiraNodes: List<Instance>,
        sshKey: SshKeyFile,
        executor: ExecutorService
    ): List<CompletableFuture<InstalledJira>> = jiraNodes
        .onEach { instance ->
            CloseableThreadContext.push("a jira node").use {
                sshKey.facilitateSsh(instance.publicIpAddress)
            }
        }
        .mapIndexed { index, instance ->
            val server = TcpServer(instance.publicIpAddress, 8080, "dc-$index")
            val node = nodes[index]
            return@mapIndexed executor.submitWithLogContext("provision ${server.name}") {
                Ssh(
                    SshHost(server.ip, "ubuntu", sshKey.path),
                    connectivityPatience = 5
                ).newConnection().use { ssh ->
                    node.installation.install(ssh, server, node.flow) // TODO include server.name in exceptions
                }
            }
        }

    private fun startNodes(
        installedNodes: List<CompletableFuture<InstalledJira>>,
        keyPath: Path
    ): List<StartedJira> = installedNodes
        .map { it.get() }
        .mapIndexed { index, installed ->
            val server = installed.server
            val node = nodes[index]
            return@mapIndexed time("start ${server.name}") {
                Ssh(
                    SshHost(server.ip, "ubuntu", keyPath),
                    connectivityPatience = 5
                ).newConnection().use { ssh ->
                    node.start.start(ssh, installed, node.flow)
                }
            }
        }

    class Builder {
        private var nodes: List<JiraNodeProvisioning> = (1..2).map { JiraNodeProvisioning.Builder().build() }
        private var loadBalancer: LoadBalancerFormula = ApacheEc2LoadBalancerFormula()
        private var sharedHomeSource: JiraHomeSource = EmptyJiraHome()
        private var computer: Computer = C4EightExtraLargeElastic()
        private var jiraVolume: Volume = Volume(100)
        private var stackPatience: Duration = Duration.ofMinutes(30)
        private var network: Network? = null

        fun nodes(nodes: List<JiraNodeProvisioning>) = apply { this.nodes = nodes }
        fun loadBalancer(loadBalancer: LoadBalancerFormula) = apply { this.loadBalancer = loadBalancer }
        fun sharedHomeSource(sharedHomeSource: JiraHomeSource) = apply { this.sharedHomeSource = sharedHomeSource }
        fun computer(computer: Computer) = apply { this.computer = computer }
        fun jiraVolume(jiraVolume: Volume) = apply { this.jiraVolume = jiraVolume }
        fun stackPatience(stackPatience: Duration) = apply { this.stackPatience = stackPatience }
        internal fun network(network: Network) = apply { this.network = network }

        fun build(): FlowDataCenterFormula = FlowDataCenterFormula(
            nodes = nodes,
            loadBalancerFormula = loadBalancer,
            sharedHomeSource = sharedHomeSource,
            computer = computer,
            volume = jiraVolume,
            stackPatience = stackPatience,
            overriddenNetwork = network
        )
    }
}
