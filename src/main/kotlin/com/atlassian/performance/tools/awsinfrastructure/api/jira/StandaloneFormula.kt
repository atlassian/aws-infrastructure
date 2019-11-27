package com.atlassian.performance.tools.awsinfrastructure.api.jira

import com.amazonaws.services.cloudformation.model.Parameter
import com.amazonaws.services.ec2.model.Tag
import com.atlassian.performance.tools.aws.api.*
import com.atlassian.performance.tools.awsinfrastructure.ApplicationStorageWrapper
import com.atlassian.performance.tools.awsinfrastructure.TemplateBuilder
import com.atlassian.performance.tools.awsinfrastructure.api.RemoteLocation
import com.atlassian.performance.tools.awsinfrastructure.api.hardware.C4EightExtraLargeElastic
import com.atlassian.performance.tools.awsinfrastructure.api.hardware.Computer
import com.atlassian.performance.tools.awsinfrastructure.api.hardware.M4ExtraLargeElastic
import com.atlassian.performance.tools.awsinfrastructure.api.hardware.Volume
import com.atlassian.performance.tools.awsinfrastructure.api.network.Network
import com.atlassian.performance.tools.awsinfrastructure.api.network.NetworkFormula
import com.atlassian.performance.tools.awsinfrastructure.api.virtualusers.InstanceAddressSelector
import com.atlassian.performance.tools.awsinfrastructure.jira.StandaloneNodeFormula
import com.atlassian.performance.tools.concurrency.api.submitWithLogContext
import com.atlassian.performance.tools.infrastructure.api.app.Apps
import com.atlassian.performance.tools.infrastructure.api.database.Database
import com.atlassian.performance.tools.infrastructure.api.distribution.ProductDistribution
import com.atlassian.performance.tools.infrastructure.api.jira.JiraHomeSource
import com.atlassian.performance.tools.infrastructure.api.jira.JiraNodeConfig
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

class StandaloneFormula private constructor(
    private val apps: Apps,
    internal val productDistribution: ProductDistribution,
    private val jiraHomeSource: JiraHomeSource,
    private val database: Database,
    private val config: JiraNodeConfig,
    private val computer: Computer,
    private val jiraVolume: Volume,
    private val stackCreationTimeout: Duration,
    private val overriddenNetwork: Network? = null,
    private val databaseComputer: Computer,
    private val databaseVolume: Volume
) : JiraFormula {

    @Suppress("DEPRECATION")
    @Deprecated(message = "Use StandaloneFormula.Builder instead.")
    constructor(
        apps: Apps,
        application: com.atlassian.performance.tools.awsinfrastructure.api.storage.ApplicationStorage,
        jiraHomeSource: JiraHomeSource,
        database: Database,
        config: JiraNodeConfig,
        computer: Computer
    ) : this(
        apps = apps,
        productDistribution = ApplicationStorageWrapper(application),
        jiraHomeSource = jiraHomeSource,
        database = database,
        config = config,
        computer = computer,
        jiraVolume = Volume(200),
        stackCreationTimeout = Duration.ofMinutes(30),
        databaseComputer = M4ExtraLargeElastic(),
        databaseVolume = Volume(100)
    )

    @Suppress("DEPRECATION")
    @Deprecated(message = "Use StandaloneFormula.Builder instead.")
    constructor (
        apps: Apps,
        application: com.atlassian.performance.tools.awsinfrastructure.api.storage.ApplicationStorage,
        jiraHomeSource: JiraHomeSource,
        database: Database
    ) : this(
        apps = apps,
        productDistribution = ApplicationStorageWrapper(application),
        jiraHomeSource = jiraHomeSource,
        database = database,
        config = JiraNodeConfig.Builder().build(),
        computer = C4EightExtraLargeElastic(),
        jiraVolume = Volume(200),
        stackCreationTimeout = Duration.ofMinutes(30),
        databaseComputer = M4ExtraLargeElastic(),
        databaseVolume = Volume(100)
    )

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
                        .withParameterValue(aws.defaultAmi),
                    Parameter()
                        .withParameterKey("JiraInstanceType")
                        .withParameterValue(computer.instanceType.toString()),
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
                pollingTimeout = stackCreationTimeout
            ).provision()
        }

        val uploadPlugins = executor.submitWithLogContext("upload plugins") {
            apps.listFiles().forEach { pluginsTransport.upload(it) }
        }

        val jiraStack = stackProvisioning.get()
        val keyPath = key.get().file.path

        val machines = jiraStack.listMachines()
        val databaseIp = InstanceAddressSelector.getReachableIpAddress(machines.single { it.tags.contains(Tag("jpt-database", "true")) })
        val databaseHost = SshHost(databaseIp, "ubuntu", keyPath)
        val databaseSsh = Ssh(databaseHost, connectivityPatience = 4)
        val jiraIp = InstanceAddressSelector.getReachableIpAddress(machines.single { it.tags.contains(Tag("jpt-jira", "true")) })
        val jiraAddress = URI("http://$jiraIp:8080/")

        val setupDatabase = executor.submitWithLogContext("database") {
            databaseSsh.newConnection().use {
                databaseComputer.setUp(it)
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

        CloseableThreadContext.push("Jira node").use {
            key.get().file.facilitateSsh(jiraIp)
        }
        val nodeFormula = StandaloneNodeFormula(
            config = config,
            jiraHomeSource = jiraHomeSource,
            pluginsTransport = pluginsTransport,
            resultsTransport = resultsTransport,
            databaseIp = databaseIp,
            productDistribution = productDistribution,
            ssh = ssh,
            computer = computer
        )

        uploadPlugins.get()

        val provisionedNode = nodeFormula.provision()

        val databaseDataLocation = setupDatabase.get()
        executor.shutdownNow()
        val node = time("start") { provisionedNode.start(emptyList()) }

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
        return@time ProvisionedJira(jira = jira, resource = jiraStack)
    }

    class Builder constructor(
        private val productDistribution: ProductDistribution,
        private val jiraHomeSource: JiraHomeSource,
        private val database: Database
    ) {
        @Suppress("DEPRECATION")
        @Deprecated("Use `ProductDistribution` instead of `ApplicationStorage`.")
        constructor(
            application: com.atlassian.performance.tools.awsinfrastructure.api.storage.ApplicationStorage,
            jiraHomeSource: JiraHomeSource,
            database: Database
        ) : this(
            productDistribution = ApplicationStorageWrapper(application),
            jiraHomeSource = jiraHomeSource,
            database = database
        )

        private var config: JiraNodeConfig = JiraNodeConfig.Builder().build()
        private var apps: Apps = Apps(emptyList())
        private var computer: Computer = C4EightExtraLargeElastic()
        private var jiraVolume: Volume = Volume(200)
        private var stackCreationTimeout: Duration = Duration.ofMinutes(30)
        private var network: Network? = null
        private var databaseComputer: Computer = M4ExtraLargeElastic()
        private var databaseVolume: Volume = Volume(100)

        internal constructor(
            formula: StandaloneFormula
        ) : this(
            productDistribution = formula.productDistribution,
            jiraHomeSource = formula.jiraHomeSource,
            database = formula.database
        ) {
            config = formula.config
            apps = formula.apps
            computer = formula.computer
            jiraVolume = formula.jiraVolume
            stackCreationTimeout = formula.stackCreationTimeout
            network = formula.overriddenNetwork
            databaseComputer = formula.databaseComputer
            databaseVolume = formula.databaseVolume
        }

        fun config(config: JiraNodeConfig): Builder = apply { this.config = config }
        fun apps(apps: Apps): Builder = apply { this.apps = apps }
        fun computer(computer: Computer): Builder = apply { this.computer = computer }
        fun jiraVolume(jiraVolume: Volume): Builder = apply { this.jiraVolume = jiraVolume }
        fun stackCreationTimeout(stackCreationTimeout: Duration): Builder =
            apply { this.stackCreationTimeout = stackCreationTimeout }

        fun databaseComputer(databaseComputer: Computer): Builder = apply { this.databaseComputer = databaseComputer }
        fun databaseVolume(databaseVolume: Volume): Builder = apply { this.databaseVolume = databaseVolume }

        internal fun network(network: Network) = apply { this.network = network }

        fun build(): StandaloneFormula = StandaloneFormula(
            apps = apps,
            productDistribution = productDistribution,
            jiraHomeSource = jiraHomeSource,
            database = database,
            config = config,
            computer = computer,
            jiraVolume = jiraVolume,
            stackCreationTimeout = stackCreationTimeout,
            overriddenNetwork = network,
            databaseComputer = databaseComputer,
            databaseVolume = databaseVolume
        )
    }
}
