package com.atlassian.performance.tools.awsinfrastructure.api.jira

import com.amazonaws.services.cloudformation.model.Parameter
import com.amazonaws.services.ec2.model.Tag
import com.atlassian.performance.tools.aws.api.*
import com.atlassian.performance.tools.awsinfrastructure.NetworkFormula
import com.atlassian.performance.tools.awsinfrastructure.TemplateBuilder
import com.atlassian.performance.tools.awsinfrastructure.api.Network
import com.atlassian.performance.tools.awsinfrastructure.api.hardware.C5NineExtraLargeEphemeral
import com.atlassian.performance.tools.awsinfrastructure.api.hardware.Computer
import com.atlassian.performance.tools.awsinfrastructure.api.hardware.M5ExtraLargeEphemeral
import com.atlassian.performance.tools.awsinfrastructure.api.hardware.Volume
import com.atlassian.performance.tools.concurrency.api.submitWithLogContext
import com.atlassian.performance.tools.infrastructure.api.jira.hook.TcpServer
import com.atlassian.performance.tools.jvmtasks.api.TaskTimer.time
import com.atlassian.performance.tools.ssh.api.Ssh
import com.atlassian.performance.tools.ssh.api.SshHost
import com.google.common.util.concurrent.ThreadFactoryBuilder
import org.apache.logging.log4j.CloseableThreadContext
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.net.URI
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.Future

class HooksServerFormula private constructor(
    private val node: JiraNodeProvisioning,
    private val jiraComputer: Computer,
    private val jiraVolume: Volume,
    private val dbComputer: Computer,
    private val dbVolume: Volume,
    private val stackCreationTimeout: Duration,
    private val network: Network?
) : JiraFormula {

    private val logger: Logger = LogManager.getLogger(this::class.java)

    override fun provision(
        investment: Investment,
        pluginsTransport: Storage,
        resultsTransport: Storage,
        key: Future<SshKey>,
        roleProfile: String,
        aws: Aws
    ): ProvisionedJira = time("provision Jira Server") {
        logger.info("Setting up Jira Server...")
        val executor = Executors.newFixedThreadPool(
            4,
            ThreadFactoryBuilder().setNameFormat("standalone-provisioning-thread-%d")
                .build()
        )
        val network = network ?: NetworkFormula(investment, aws).provision()
        val template = TemplateBuilder("single-node.yaml").build()
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
                        .withParameterValue(jiraComputer.instanceType.toString()),
                    Parameter()
                        .withParameterKey("JiraVolumeSize")
                        .withParameterValue(jiraVolume.size.toString()),
                    Parameter()
                        .withParameterKey("DatabaseInstanceType")
                        .withParameterValue(dbComputer.instanceType.toString()),
                    Parameter()
                        .withParameterKey("DatabaseVolumeSize")
                        .withParameterValue(dbVolume.size.toString()),
                    Parameter()
                        .withParameterKey("Vpc")
                        .withParameterValue(network.vpc.vpcId),
                    Parameter()
                        .withParameterKey("Subnet")
                        .withParameterValue(network.subnet.subnetId)
                ),
                aws = aws,
                pollingTimeout = stackCreationTimeout
            ).provision()
        }
        val stack = stackProvisioning.get()
        val keyPath = key.get().file.path
        val machines = stack.listMachines()
        val jiraIp = machines.single { it.tags.contains(Tag("jpt-jira", "true")) }.publicIpAddress
        val jiraServer = TcpServer(jiraIp, 8080, "jira")
        val jiraAddress = jiraServer.toPublicHttp()
        val jiraSsh = Ssh(SshHost(jiraIp, "ubuntu", keyPath), connectivityPatience = 5)
        val installedJira = jiraSsh.newConnection().use { ssh ->
            node.installation.install(ssh, jiraServer, node.hooks)
        }
        CloseableThreadContext.push("Jira node").use {
            key.get().file.facilitateSsh(jiraIp)
        }
        executor.shutdownNow()
        time("start") {
            jiraSsh.newConnection().use { ssh ->
                node.start.start(ssh, installedJira, node.hooks)
            }
        }
        val jira = minimumFeatures(
            jiraAddress,
            listOf(StartedNode.legacyHooks(node.hooks, "jira", resultsTransport, jiraSsh))
        )
        logger.info("$jira is set up, will expire ${stack.expiry}")
        return@time ProvisionedJira(jira = jira, resource = stack)
    }

    class Builder {
        private var node: JiraNodeProvisioning = JiraNodeProvisioning.Builder().build()
        private var jiraComputer: Computer = C5NineExtraLargeEphemeral()
        private var jiraVolume: Volume = Volume(200)
        private var dbComputer: Computer = M5ExtraLargeEphemeral()
        private var dbVolume: Volume = Volume(100)
        private var network: Network? = null
        private var stackCreationTimeout: Duration = Duration.ofMinutes(30)

        fun node(node: JiraNodeProvisioning) = apply { this.node = node }
        internal fun network(network: Network) = apply { this.network = network }

        fun build(): HooksServerFormula = HooksServerFormula(
            node = node,
            jiraComputer = jiraComputer,
            jiraVolume = jiraVolume,
            dbComputer = dbComputer,
            dbVolume = dbVolume,
            stackCreationTimeout = stackCreationTimeout,
            network = network
        )
    }
}

private fun TcpServer.toPublicHttp() = URI("http://$ip:$publicPort/")
