package com.atlassian.performance.tools.awsinfrastructure.api.jira

import com.amazonaws.services.cloudformation.model.Parameter
import com.amazonaws.services.ec2.model.Tag
import com.atlassian.performance.tools.aws.api.*
import com.atlassian.performance.tools.awsinfrastructure.TemplateBuilder
import com.atlassian.performance.tools.awsinfrastructure.api.RemoteLocation
import com.atlassian.performance.tools.awsinfrastructure.api.storage.ApplicationStorage
import com.atlassian.performance.tools.awsinfrastructure.jira.StandaloneNodeFormula
import com.atlassian.performance.tools.concurrency.api.submitWithLogContext
import com.atlassian.performance.tools.infrastructure.api.app.Apps
import com.atlassian.performance.tools.infrastructure.api.database.Database
import com.atlassian.performance.tools.infrastructure.api.jira.JiraHomeSource
import com.atlassian.performance.tools.infrastructure.api.jira.JiraNodeConfig
import com.atlassian.performance.tools.jvmtasks.api.TaskTimer.time
import com.atlassian.performance.tools.ssh.api.Ssh
import com.atlassian.performance.tools.ssh.api.SshHost
import com.google.common.util.concurrent.ThreadFactoryBuilder
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.net.URI
import java.util.concurrent.Executors
import java.util.concurrent.Future

class StandaloneFormula(
    private val apps: Apps,
    private val application: ApplicationStorage,
    private val jiraHomeSource: JiraHomeSource,
    private val database: Database,
    private val config: JiraNodeConfig = JiraNodeConfig(),
    private val fastNonpersistentStorage: Boolean = true
) : JiraFormula {
    private val logger: Logger = LogManager.getLogger(this::class.java)

    override fun provision(
        investment: Investment,
        pluginsTransport: Storage,
        resultsTransport: Storage,
        key: Future<SshKey>,
        roleProfile: String,
        aws: Aws
    ): ProvisionedJira {
        logger.info("Setting up Jira...")

        val executor = Executors.newFixedThreadPool(
            4,
            ThreadFactoryBuilder().setNameFormat("standalone-provisioning-thread-%d")
                .build()
        )

        val template = TemplateBuilder("single-node.yaml").adaptTo(listOf(config))

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
                        .withParameterValue(aws.defaultAmi)
                ),
                aws = aws
            ).provision()
        }

        val uploadPlugins = executor.submitWithLogContext("upload plugins") {
            apps.listFiles().forEach { pluginsTransport.upload(it) }
        }

        val jiraStack = stackProvisioning.get()
        val keyPath = key.get().file.path

        val machines = jiraStack.listMachines()
        val databaseIp = machines.single { it.tags.contains(Tag("jpt-database", "true")) }.publicIpAddress
        val databaseHost = SshHost(databaseIp, "ubuntu", keyPath)
        val databaseSsh = Ssh(databaseHost)
        val jiraIp = machines.single { it.tags.contains(Tag("jpt-jira", "true")) }.publicIpAddress
        val jiraAddress = URI("http://$jiraIp:8080/")

        val setupDatabase = executor.submitWithLogContext("database") {
            databaseSsh.newConnection().use {
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

        val ssh = Ssh(SshHost(jiraIp, "ubuntu", keyPath), connectivityPatience = 5)

        key.get().file.facilitateSsh(jiraIp)
        val nodeFormula = StandaloneNodeFormula(
            config = config,
            jiraHomeSource = jiraHomeSource,
            pluginsTransport = pluginsTransport,
            resultsTransport = resultsTransport,
            databaseIp = databaseIp,
            application = application,
            ssh = ssh,
            ephemeralDrive = fastNonpersistentStorage
        )

        uploadPlugins.get()

        val provisionedNode = nodeFormula.provision()

        val databaseDataLocation = setupDatabase.get()
        executor.shutdownNow()
        val node = time("start") { provisionedNode.start() }

        val jira = Jira(
            nodes = listOf(node),
            jiraHome = RemoteLocation(
                ssh.host,
                provisionedNode.jiraHome
            ),
            database = databaseDataLocation,
            address = jiraAddress,
            jmxClients = listOf(config.remoteJmx.getClient(jiraIp))
        )
        logger.info("$jira is set up, will expire ${jiraStack.expiry}")
        return ProvisionedJira(jira = jira, resource = jiraStack)
    }
}