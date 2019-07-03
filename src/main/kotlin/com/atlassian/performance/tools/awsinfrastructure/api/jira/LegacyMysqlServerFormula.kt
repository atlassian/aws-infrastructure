package com.atlassian.performance.tools.awsinfrastructure.api.jira

import com.amazonaws.services.cloudformation.model.Parameter
import com.amazonaws.services.ec2.model.Tag
import com.atlassian.performance.tools.aws.api.*
import com.atlassian.performance.tools.awsinfrastructure.Network
import com.atlassian.performance.tools.awsinfrastructure.NetworkFormula
import com.atlassian.performance.tools.awsinfrastructure.TemplateBuilder
import com.atlassian.performance.tools.awsinfrastructure.api.DatasetCatalogue
import com.atlassian.performance.tools.awsinfrastructure.api.RemoteLocation
import com.atlassian.performance.tools.awsinfrastructure.api.hardware.C5NineExtraLargeEphemeral
import com.atlassian.performance.tools.awsinfrastructure.api.hardware.Computer
import com.atlassian.performance.tools.awsinfrastructure.api.hardware.M5ExtraLargeEphemeral
import com.atlassian.performance.tools.awsinfrastructure.api.hardware.Volume
import com.atlassian.performance.tools.concurrency.api.submitWithLogContext
import com.atlassian.performance.tools.infrastructure.api.database.Database
import com.atlassian.performance.tools.infrastructure.api.database.DatabaseIpConfig
import com.atlassian.performance.tools.infrastructure.api.database.MysqlConnector
import com.atlassian.performance.tools.infrastructure.api.distribution.PublicJiraSoftwareDistribution
import com.atlassian.performance.tools.infrastructure.api.jira.flow.JiraNodeFlow
import com.atlassian.performance.tools.infrastructure.api.jira.flow.TcpServer
import com.atlassian.performance.tools.infrastructure.api.jira.flow.install.DefaultJiraInstallation
import com.atlassian.performance.tools.infrastructure.api.jira.flow.install.HookedJiraInstallation
import com.atlassian.performance.tools.infrastructure.api.jira.flow.install.JiraInstallation
import com.atlassian.performance.tools.infrastructure.api.jira.flow.start.HookedJiraStart
import com.atlassian.performance.tools.infrastructure.api.jira.flow.start.JiraLaunchScript
import com.atlassian.performance.tools.infrastructure.api.jira.flow.start.JiraStart
import com.atlassian.performance.tools.infrastructure.api.jvm.OracleJDK
import com.atlassian.performance.tools.jvmtasks.api.TaskTimer.time
import com.atlassian.performance.tools.ssh.api.Ssh
import com.atlassian.performance.tools.ssh.api.SshHost
import com.google.common.util.concurrent.ThreadFactoryBuilder
import org.apache.logging.log4j.CloseableThreadContext
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.net.URI
import java.nio.file.Paths
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.Future

class LegacyMysqlServerFormula private constructor(
    private val installation: JiraInstallation,
    private val start: JiraStart,
    private val flow: JiraNodeFlow,
    private val database: Database,
    private val jiraComputer: Computer,
    private val jiraVolume: Volume,
    private val dbComputer: Computer,
    private val dbVolume: Volume,
    private val stackCreationTimeout: Duration,
    private val overriddenNetwork: Network?
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
        logger.info("Setting up Jira...")

        val executor = Executors.newFixedThreadPool(
            4,
            ThreadFactoryBuilder().setNameFormat("standalone-provisioning-thread-%d")
                .build()
        )
        val network = overriddenNetwork ?: NetworkFormula(investment, aws).provision()
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

        val jiraStack = stackProvisioning.get()
        val keyPath = key.get().file.path

        val machines = jiraStack.listMachines()
        val databaseIp = machines.single { it.tags.contains(Tag("jpt-database", "true")) }.publicIpAddress
        val databaseHost = SshHost(databaseIp, "ubuntu", keyPath)
        val databaseSsh = Ssh(databaseHost, connectivityPatience = 4)
        val jiraIp = machines.single { it.tags.contains(Tag("jpt-jira", "true")) }.publicIpAddress
        val jiraServer = TcpServer(jiraIp, 8080, "jira")
        val jiraAddress = jiraServer.toPublicHttp()

        val setupDatabase = executor.submitWithLogContext("database") {
            databaseSsh.newConnection().use {
                dbComputer.setUp(it)
                logger.info("Setting up database...")
                key.get().file.facilitateSsh(databaseIp)
                val location = database.setup(it)
                logger.info("Database is set up")
                logger.info("Starting database...")
                database.start(jiraAddress, it)
                logger.info("Database is started")
                RemoteLocation(databaseHost, location)
            }
        }

        val jiraSsh = Ssh(SshHost(jiraIp, "ubuntu", keyPath), connectivityPatience = 5)
        flow.hookPostInstall(MysqlConnector())
        flow.hookPostInstall(DatabaseIpConfig(databaseIp))
        val installedJira = jiraSsh.newConnection().use { ssh ->
            installation.install(ssh, jiraServer, flow)
        }
        val databaseLocation = setupDatabase.get()
        CloseableThreadContext.push("Jira node").use {
            key.get().file.facilitateSsh(jiraIp)
        }
        executor.shutdownNow()
        time("start") {
            jiraSsh.newConnection().use { ssh ->
                start.start(ssh, installedJira, flow)
            }
        }
        val jira = Jira(
            emptyList(),
            RemoteLocation(SshHost("UNSUPPORTED", "UNSUPPORTED", Paths.get(".")), "UNSUPPORTED"),
            databaseLocation,
            jiraAddress,
            emptyList()
        )
        return@time ProvisionedJira(jira = jira, resource = jiraStack)
    }

    class Builder {
        private var installation: JiraInstallation = HookedJiraInstallation(DefaultJiraInstallation(
            jiraHomeSource = DatasetCatalogue().largeJiraSeven().jiraHomeSource,
            productDistribution = PublicJiraSoftwareDistribution("7.13.0"),
            jdk = OracleJDK()
        ))
        private var start: JiraStart = HookedJiraStart(JiraLaunchScript())
        private var flow: JiraNodeFlow = JiraNodeFlow()
        private var database: Database = DatasetCatalogue().largeJiraSeven().database
        private var jiraComputer: Computer = C5NineExtraLargeEphemeral()
        private var jiraVolume: Volume = Volume(200)
        private var dbComputer: Computer = M5ExtraLargeEphemeral()
        private var dbVolume: Volume = Volume(100)
        private var overriddenNetwork: Network? = null
        private var stackCreationTimeout: Duration = Duration.ofMinutes(30)

        fun installation(installation: JiraInstallation) = apply { this.installation = installation }
        fun start(start: JiraStart) = apply { this.start = start }
        fun flow(flow: JiraNodeFlow) = apply { this.flow = flow }

        fun build(): LegacyMysqlServerFormula = LegacyMysqlServerFormula(
            installation = installation,
            start = start,
            flow = flow,
            database = database,
            jiraComputer = jiraComputer,
            jiraVolume = jiraVolume,
            dbComputer = dbComputer,
            dbVolume = dbVolume,
            stackCreationTimeout = stackCreationTimeout,
            overriddenNetwork = overriddenNetwork
        )
    }
}

private fun TcpServer.toPublicHttp() = URI("http://$ip:$publicPort/")
