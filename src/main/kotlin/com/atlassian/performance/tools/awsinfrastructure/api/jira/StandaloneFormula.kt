package com.atlassian.performance.tools.awsinfrastructure.api.jira

import com.amazonaws.services.cloudformation.model.Parameter
import com.atlassian.performance.tools.aws.api.*
import com.atlassian.performance.tools.awsinfrastructure.InstanceFilters
import com.atlassian.performance.tools.awsinfrastructure.TemplateBuilder
import com.atlassian.performance.tools.awsinfrastructure.api.RemoteLocation
import com.atlassian.performance.tools.awsinfrastructure.api.hardware.C4EightExtraLargeElastic
import com.atlassian.performance.tools.awsinfrastructure.api.hardware.Computer
import com.atlassian.performance.tools.awsinfrastructure.api.hardware.M4ExtraLargeElastic
import com.atlassian.performance.tools.awsinfrastructure.api.hardware.Volume
import com.atlassian.performance.tools.awsinfrastructure.api.network.Network
import com.atlassian.performance.tools.awsinfrastructure.api.network.NetworkFormula
import com.atlassian.performance.tools.awsinfrastructure.api.network.access.*
import com.atlassian.performance.tools.awsinfrastructure.jira.StandaloneNodeFormula
import com.atlassian.performance.tools.concurrency.api.AbruptExecutorService
import com.atlassian.performance.tools.concurrency.api.submitWithLogContext
import com.atlassian.performance.tools.infrastructure.api.app.Apps
import com.atlassian.performance.tools.infrastructure.api.database.Database
import com.atlassian.performance.tools.infrastructure.api.distribution.ProductDistribution
import com.atlassian.performance.tools.infrastructure.api.jira.JiraHomeSource
import com.atlassian.performance.tools.infrastructure.api.jira.JiraNodeConfig
import com.atlassian.performance.tools.jvmtasks.api.TaskScope.task
import com.atlassian.performance.tools.ssh.api.Ssh
import com.atlassian.performance.tools.ssh.api.SshHost
import com.google.common.util.concurrent.ThreadFactoryBuilder
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.net.URI
import java.time.Duration
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.function.Supplier

/**
 * The EC2 instances provisioned with this class will have 'instance initiated shutdown' parameter set to 'terminate'.
 */
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
    private val databaseVolume: Volume,
    private val accessRequester: AccessRequester,
    private val adminPasswordPlainText: String,
    private val waitForUpgrades: Boolean
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

        return AbruptExecutorService(
            Executors.newFixedThreadPool(
                4,
                ThreadFactoryBuilder()
                    .setNameFormat("standalone-provisioning-thread-%d")
                    .build()
            )
        ).use { executor ->
            provision(
                executor = executor,
                investment = investment,
                pluginsTransport = pluginsTransport,
                resultsTransport = resultsTransport,
                key = key,
                roleProfile = roleProfile,
                aws = aws
            )
        }
    }

    private fun provision(
        executor: ExecutorService,
        investment: Investment,
        pluginsTransport: Storage,
        resultsTransport: Storage,
        key: Future<SshKey>,
        roleProfile: String,
        aws: Aws
    ): ProvisionedJira {
        val provisionedNetwork = NetworkFormula(investment, aws).reuseOrProvision(overriddenNetwork)
        val network = provisionedNetwork.network
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

        val jiraMachine = InstanceFilters().jiraInstances(machines).single()
        val jiraPublicIp = jiraMachine.publicIpAddress
        val jiraPublicHttpAddress = URI("http://$jiraPublicIp:8080/")
        val jiraSshIp = jiraMachine.publicIpAddress
        val jiraSsh = Ssh(SshHost(jiraSshIp, "ubuntu", keyPath), connectivityPatience = 5)

        val databaseMachine = InstanceFilters().dbInstance(machines)
        val databasePrivateIp = databaseMachine.privateIpAddress
        val databaseSshIp = databaseMachine.publicIpAddress
        val databaseSsh = Ssh(SshHost(databaseSshIp, "ubuntu", keyPath), connectivityPatience = 4)

        key.get().file.facilitateSsh(jiraSshIp)
        val nodeFormula = StandaloneNodeFormula(
            config = config,
            jiraHomeSource = jiraHomeSource,
            pluginsTransport = pluginsTransport,
            resultsTransport = resultsTransport,
            databaseIp = databasePrivateIp,
            productDistribution = productDistribution,
            ssh = jiraSsh,
            computer = computer,
            adminPasswordPlainText = adminPasswordPlainText,
            waitForUpgrades = waitForUpgrades
        )

        val jiraNodeSecurityGroup = jiraStack.findSecurityGroup("JiraNodeSecurityGroup")
        val jiraNodeHttpAccessProvider = SecurityGroupIngressAccessProvider
            .Builder(ec2 = aws.ec2, securityGroup = jiraNodeSecurityGroup, portRange = 8080..8080).build()
        val jiraNodeJvmDebugAccessProvider = MultiAccessProvider(
            config.debug.getRequiredPorts().toSet().map {
                SecurityGroupIngressAccessProvider
                    .Builder(ec2 = aws.ec2, securityGroup = jiraNodeSecurityGroup, portRange = it..it).build()
            }
        )
        val jiraNodeJmxAccessProvider = MultiAccessProvider(
            config.remoteJmx.getRequiredPorts().toSet().map {
                SecurityGroupIngressAccessProvider
                    .Builder(ec2 = aws.ec2, securityGroup = jiraNodeSecurityGroup, portRange = it..it).build()
            }
        )
        val jiraNodeSplunkForwarderAccessProvider = MultiAccessProvider(
            config.splunkForwarder.getRequiredPorts().toSet().map {
                SecurityGroupIngressAccessProvider
                    .Builder(ec2 = aws.ec2, securityGroup = jiraNodeSecurityGroup, portRange = it..it).build()
            }
        )
        val jiraAccessProvider = MultiAccessProvider(
            listOf(
                jiraNodeHttpAccessProvider,
                jiraNodeJvmDebugAccessProvider,
                jiraNodeJmxAccessProvider,
                jiraNodeSplunkForwarderAccessProvider
            )
        )

        val selfDashboardAccess = executor.submitWithLogContext("self dashboard access") {
            ForIpAccessRequester(Supplier { jiraPublicIp }).requestAccess(jiraNodeHttpAccessProvider)
        }

        val externalAccess = executor.submitWithLogContext("external access") {
            accessRequester.requestAccess(jiraAccessProvider)
        }

        val setupDatabase = executor.submitWithLogContext("database") {
            databaseSsh.newConnection().use {
                databaseComputer.setUp(it)
                logger.info("Setting up database...")
                key.get().file.facilitateSsh(databaseSshIp)
                val databaseDataLocation = database.setup(it)
                logger.info("Database is set up")
                logger.info("Starting database...")
                database.start(jiraPublicHttpAddress, it)
                logger.info("Database is started")
                RemoteLocation(databaseSsh.host, databaseDataLocation)
            }
        }

        // node provisioning relies on plugins being uploaded
        uploadPlugins.get()
        val provisionedNode = nodeFormula.provision()

        val databaseDataLocation = setupDatabase.get()
        val node = task("start", Callable { provisionedNode.start(emptyList()) })

        if (!selfDashboardAccess.get()) {
            logger.warn("It's possible that Jira doesn't have HTTP access to itself. Dashboards may not work.")
        }

        if (!externalAccess.get()) {
            logger.warn("It's possible that defined external access to Jira resources (e.g. http, debug, splunk) wasn't granted.")
        }

        val jira = Jira.Builder(
            nodes = listOf(node),
            jiraHome = RemoteLocation(
                jiraSsh.host,
                provisionedNode.jiraHome
            ),
            database = databaseDataLocation,
            address = jiraPublicHttpAddress
        )
            .jmxClients(listOf(config.remoteJmx.getClient(jiraPublicIp)))
            .build()

        logger.info("$jira is set up, will expire ${jiraStack.expiry}")
        return ProvisionedJira.Builder(jira)
            .resource(
                DependentResources(
                    user = jiraStack,
                    dependency = provisionedNetwork.resource
                )
            )
            .accessProvider(jiraAccessProvider)
            .build()
    }

    class Builder(
        private val productDistribution: ProductDistribution,
        private val jiraHomeSource: JiraHomeSource,
        private val database: Database
    ) {
        private var config: JiraNodeConfig = JiraNodeConfig.Builder().build()
        private var apps: Apps = Apps(emptyList())
        private var computer: Computer = C4EightExtraLargeElastic()
        private var jiraVolume: Volume = Volume(200)
        private var stackCreationTimeout: Duration = Duration.ofMinutes(30)
        private var network: Network? = null
        private var databaseComputer: Computer = M4ExtraLargeElastic()
        private var databaseVolume: Volume = Volume(100)
        private var accessRequester: AccessRequester = ForIpAccessRequester(LocalPublicIpv4Provider.Builder().build())
        private var adminPasswordPlainText: String = "admin"
        private var waitForUpgrades: Boolean = true

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
            accessRequester = formula.accessRequester
            adminPasswordPlainText = formula.adminPasswordPlainText
            waitForUpgrades = formula.waitForUpgrades
        }

        fun config(config: JiraNodeConfig): Builder = apply { this.config = config }
        fun apps(apps: Apps): Builder = apply { this.apps = apps }
        fun computer(computer: Computer): Builder = apply { this.computer = computer }
        fun jiraVolume(jiraVolume: Volume): Builder = apply { this.jiraVolume = jiraVolume }
        fun stackCreationTimeout(stackCreationTimeout: Duration): Builder =
            apply { this.stackCreationTimeout = stackCreationTimeout }

        fun databaseComputer(databaseComputer: Computer): Builder = apply { this.databaseComputer = databaseComputer }
        fun databaseVolume(databaseVolume: Volume): Builder = apply { this.databaseVolume = databaseVolume }

        fun adminPasswordPlainText(adminPasswordPlainText: String): Builder =
            apply { this.adminPasswordPlainText = adminPasswordPlainText }

        internal fun network(network: Network) = apply { this.network = network }

        fun accessRequester(accessRequester: AccessRequester) = apply { this.accessRequester = accessRequester }

        fun waitForUpgrades(waitForUpgrades: Boolean) = apply { this.waitForUpgrades = waitForUpgrades }

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
            databaseVolume = databaseVolume,
            accessRequester = accessRequester,
            adminPasswordPlainText = adminPasswordPlainText,
            waitForUpgrades = waitForUpgrades
        )
    }
}
