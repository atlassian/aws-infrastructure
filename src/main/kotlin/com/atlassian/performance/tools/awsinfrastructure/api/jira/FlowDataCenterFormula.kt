package com.atlassian.performance.tools.awsinfrastructure.api.jira

import com.amazonaws.services.cloudformation.model.Parameter
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
import com.atlassian.performance.tools.awsinfrastructure.jira.home.SharedHomeFormula
import com.atlassian.performance.tools.awsinfrastructure.loadbalancer.ApacheProxyFix
import com.atlassian.performance.tools.concurrency.api.submitWithLogContext
import com.atlassian.performance.tools.infrastructure.api.jira.flow.JiraNodeFlow
import com.atlassian.performance.tools.infrastructure.api.jira.flow.TcpServer
import com.atlassian.performance.tools.jvmtasks.api.TaskTimer.time
import com.atlassian.performance.tools.ssh.api.Ssh
import com.atlassian.performance.tools.ssh.api.SshHost
import org.apache.logging.log4j.CloseableThreadContext
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.Future

class FlowDataCenterFormula private constructor(
    private val flows: List<JiraNodeFlow>,
    private val loadBalancerFormula: LoadBalancerFormula,
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
        logger.info("Setting up Jira...")

        val executor = Executors.newCachedThreadPool {
            Thread(it, "dc-provisioning-${it.hashCode()}")
        }
        val network = overriddenNetwork ?: NetworkFormula(investment, aws).provision()
        val template = TemplateBuilder("2-nodes-dc.yaml").adaptTo(configs)
        val stackProvisioning = executor.submitWithLogContext("provision stack") {
            StackFormula(
                investment = investment,
                cloudformationTemplate = template,
                parameters = listOf(
                    Parameter()
                        .withParameterKey("KeyName")
                        .withParameterValue(key.get().remote.name),
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

        val jiraStack = stackProvisioning.get()
        val keyPath = key.get().file.path

        val machines = jiraStack.listMachines()
        val jiraNodes = machines.filter { it.tags.contains(Tag("jpt-jira", "true")) }
        val sharedHomeMachine = machines.single { it.tags.contains(Tag("jpt-shared-home", "true")) }
        val sharedHomeIp = sharedHomeMachine.publicIpAddress
        val sharedHomePrivateIp = sharedHomeMachine.privateIpAddress
        val sharedHomeSsh = Ssh(
            host = SshHost(sharedHomeIp, "ubuntu", keyPath),
            connectivityPatience = 4
        )
        val futureLoadBalancer = executor.submitWithLogContext("provision load balancer") {
            loadBalancerFormula.provision(
                investment = investment,
                instances = jiraNodes,
                subnet = network.subnet,
                vpc = network.vpc,
                key = key.get(),
                aws = aws
            )
        }

        val sharedHome = executor.submitWithLogContext("provision shared home") {
            logger.info("Setting up shared home...")
            key.get().file.facilitateSsh(sharedHomeIp)
            val sharedHome = SharedHomeFormula(
                jiraHomeSource = jiraHomeSource,
                pluginsTransport = pluginsTransport,
                ip = sharedHomePrivateIp,
                ssh = sharedHomeSsh,
                computer = computer
            ).provision()
            logger.info("Shared home is set up")
            sharedHome
        }

        val installedNodes = jiraNodes
            .asSequence()
            .onEach { instance ->
                CloseableThreadContext.push("a jira node").use {
                    key.get().file.facilitateSsh(instance.publicIpAddress)
                }
            }
            .mapIndexed { index, instance ->
                val server = TcpServer(instance.publicIpAddress, 8080, "dc-$index")
                val flow = flows[index]
                executor.submitWithLogContext("provision ${server.name}") {
                    Ssh(
                        SshHost(server.ip, "ubuntu", keyPath),
                        connectivityPatience = 5
                    ).newConnection().use { ssh ->
                        flow.installation.install(ssh, server, flow) // TODO include server.name in exceptions
                    }
                }
            }
            .toList()

        val provisionedLoadBalancer = futureLoadBalancer.get()
        val loadBalancer = provisionedLoadBalancer.loadBalancer
        if (loadBalancer is ApacheProxyLoadBalancer) {
            flows.forEach { it.hookPreStart(ApacheProxyFix(loadBalancer.uri)) }
            listOf(loadBalancer::updateJiraConfiguration)
        }
        val startedNodes = installedNodes
            .map { it.get() }
            .mapIndexed { index, installed ->
                val server = installed.server
                val flow = flows[index]
                time("start ${server.name}") {
                    Ssh(
                        SshHost(server.ip, "ubuntu", keyPath),
                        connectivityPatience = 5
                    ).newConnection().use { ssh ->
                        flow.installation.install(ssh, server, flow)
                    }
                }
            }

        executor.shutdownNow()

        time("wait for loadbalancer") {
            loadBalancer.waitUntilHealthy(Duration.ofMinutes(5))
        }

        val jira = minimumFeatures(loadBalancer.uri)
        logger.info("$jira is set up, will expire ${jiraStack.expiry}")
        return@time ProvisionedJira(
            jira = jira,
            resource = DependentResources(
                user = provisionedLoadBalancer.resource,
                dependency = jiraStack
            )
        )
    }

    class Builder {

        private var nodes: List<JiraNodeFlow> = (1..2).map { JiraNodeFlow.Builder().build() }
        private var loadBalancer: LoadBalancerFormula = ApacheEc2LoadBalancerFormula()
        private var computer: Computer = C4EightExtraLargeElastic()
        private var jiraVolume: Volume = Volume(100)
        private var stackPatience: Duration = Duration.ofMinutes(30)
        private var network: Network? = null

        fun nodes(nodes: List<JiraNodeFlow>) = apply { this.nodes = nodes }
        fun loadBalancer(loadBalancer: LoadBalancerFormula) = apply { this.loadBalancer = loadBalancer }
        fun computer(computer: Computer) = apply { this.computer = computer }
        fun jiraVolume(jiraVolume: Volume) = apply { this.jiraVolume = jiraVolume }
        fun stackPatience(stackPatience: Duration) = apply { this.stackPatience = stackPatience }
        internal fun network(network: Network) = apply { this.network = network }

        fun build(): FlowDataCenterFormula = FlowDataCenterFormula(
            flows = nodes,
            loadBalancerFormula = loadBalancer,
            computer = computer,
            volume = jiraVolume,
            stackPatience = stackPatience,
            overriddenNetwork = network
        )
    }
}
