package com.atlassian.performance.tools.awsinfrastructure.api.jira

import com.amazonaws.services.cloudformation.model.Parameter
import com.amazonaws.services.ec2.model.Tag
import com.atlassian.performance.tools.aws.api.*
import com.atlassian.performance.tools.awsinfrastructure.ApplicationStorageWrapper
import com.atlassian.performance.tools.awsinfrastructure.Network
import com.atlassian.performance.tools.awsinfrastructure.NetworkFormula
import com.atlassian.performance.tools.awsinfrastructure.TemplateBuilder
import com.atlassian.performance.tools.awsinfrastructure.api.RemoteLocation
import com.atlassian.performance.tools.awsinfrastructure.api.hardware.C4EightExtraLargeElastic
import com.atlassian.performance.tools.awsinfrastructure.api.hardware.Computer
import com.atlassian.performance.tools.awsinfrastructure.api.hardware.M4ExtraLargeElastic
import com.atlassian.performance.tools.awsinfrastructure.api.loadbalancer.ElasticLoadBalancerFormula
import com.atlassian.performance.tools.awsinfrastructure.api.loadbalancer.LoadBalancerFormula
import com.atlassian.performance.tools.awsinfrastructure.jira.DataCenterNodeFormula
import com.atlassian.performance.tools.awsinfrastructure.jira.DiagnosableNodeFormula
import com.atlassian.performance.tools.awsinfrastructure.jira.StandaloneNodeFormula
import com.atlassian.performance.tools.awsinfrastructure.jira.home.SharedHomeFormula
import com.atlassian.performance.tools.concurrency.api.submitWithLogContext
import com.atlassian.performance.tools.infrastructure.api.app.Apps
import com.atlassian.performance.tools.infrastructure.api.database.Database
import com.atlassian.performance.tools.infrastructure.api.jira.JiraHomeSource
import com.atlassian.performance.tools.infrastructure.api.jira.JiraNodeConfig
import com.atlassian.performance.tools.infrastructure.api.distribution.ProductDistribution
import com.atlassian.performance.tools.jvmtasks.api.TaskTimer.time
import com.atlassian.performance.tools.ssh.api.Ssh
import com.atlassian.performance.tools.ssh.api.SshHost
import com.google.common.util.concurrent.ThreadFactoryBuilder
import org.apache.logging.log4j.CloseableThreadContext
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.Future

/**
 * @param [configs] applied to nodes in the same order as they are provisioned and started
 * @param [computer] hardware specs used by the Jira nodes and the shared home node
 */
class DataCenterFormula private constructor(
    private val configs: List<JiraNodeConfig>,
    private val loadBalancerFormula: LoadBalancerFormula,
    private val apps: Apps,
    private val productDistribution: ProductDistribution,
    private val jiraHomeSource: JiraHomeSource,
    private val database: Database,
    private val computer: Computer,
    private val stackCreationTimeout: Duration,
    private val overriddenNetwork: Network? = null,
    private val databaseComputer: Computer
) : JiraFormula {
    private val logger: Logger = LogManager.getLogger(this::class.java)

    @Suppress("DEPRECATION")
    @Deprecated(message = "Use DataCenterFormula.Builder instead.")
    constructor(
        configs: List<JiraNodeConfig>,
        loadBalancerFormula: LoadBalancerFormula,
        apps: Apps,
        application: com.atlassian.performance.tools.awsinfrastructure.api.storage.ApplicationStorage,
        jiraHomeSource: JiraHomeSource,
        database: Database,
        computer: Computer
    ) : this(
        configs = configs,
        loadBalancerFormula = loadBalancerFormula,
        apps = apps,
        productDistribution = ApplicationStorageWrapper(application),
        jiraHomeSource = jiraHomeSource,
        database = database,
        computer = computer,
        stackCreationTimeout = Duration.ofMinutes(30),
        databaseComputer = M4ExtraLargeElastic()
    )

    @Suppress("DEPRECATION")
    @Deprecated(message = "Use DataCenterFormula.Builder instead.")
    constructor(
        apps: Apps,
        application: com.atlassian.performance.tools.awsinfrastructure.api.storage.ApplicationStorage,
        jiraHomeSource: JiraHomeSource,
        database: Database
    ) : this(
        configs = (1..2).map { JiraNodeConfig.Builder().name("jira-node-$it").build() },
        loadBalancerFormula = ElasticLoadBalancerFormula(),
        apps = apps,
        productDistribution = ApplicationStorageWrapper(application),
        jiraHomeSource = jiraHomeSource,
        database = database,
        computer = C4EightExtraLargeElastic(),
        stackCreationTimeout = Duration.ofMinutes(30),
        databaseComputer = M4ExtraLargeElastic()
    )

    override fun provision(
        investment: Investment,
        pluginsTransport: Storage,
        resultsTransport: Storage,
        key: Future<SshKey>,
        roleProfile: String,
        aws: Aws
    ): ProvisionedJira = time("provision Jira Data Center") {
        logger.info("Setting up Jira...")

        val executor = Executors.newCachedThreadPool(
            ThreadFactoryBuilder()
                .setNameFormat("data-center-provisioning-thread-%d")
                .build()
        )
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
                        .withParameterKey("DatabaseInstanceType")
                        .withParameterValue(databaseComputer.instanceType.toString()),
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
        val jiraNodes = machines.filter { it.tags.contains(Tag("jpt-jira", "true")) }
        val databaseIp = machines.single { it.tags.contains(Tag("jpt-database", "true")) }.publicIpAddress
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

        uploadPlugins.get()
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

        val nodeFormulas = jiraNodes
            .asSequence()
            .map { it.publicIpAddress }
            .onEach { ip ->
                CloseableThreadContext.push("a jira node").use {
                    key.get().file.facilitateSsh(ip)
                }
            }
            .map { Ssh(SshHost(it, "ubuntu", keyPath), connectivityPatience = 5) }
            .mapIndexed { i: Int, ssh: Ssh ->
                DiagnosableNodeFormula(
                    delegate = DataCenterNodeFormula(
                        base = StandaloneNodeFormula(
                            resultsTransport = resultsTransport,
                            databaseIp = databaseIp,
                            jiraHomeSource = jiraHomeSource,
                            pluginsTransport = pluginsTransport,
                            productDistribution = productDistribution,
                            ssh = ssh,
                            config = configs[i],
                            computer = computer
                        ),
                        nodeIndex = i,
                        sharedHome = sharedHome
                    )
                )
            }
            .toList()

        val databaseHost = SshHost(databaseIp, "ubuntu", keyPath)
        val databaseSsh = Ssh(databaseHost, connectivityPatience = 5)
        val provisionedLoadBalancer = futureLoadBalancer.get()
        val loadBalancer = provisionedLoadBalancer.loadBalancer
        val setupDatabase = executor.submitWithLogContext("database") {
            databaseSsh.newConnection().use {
                databaseComputer.setUp(it)
                logger.info("Setting up database...")
                key.get().file.facilitateSsh(databaseIp)
                val location = database.setup(it)
                logger.info("Database is set up")
                logger.info("Starting database...")
                database.start(loadBalancer.uri, it)
                logger.info("Database is started")
                RemoteLocation(databaseHost, location)
            }
        }

        val nodesProvisioning = nodeFormulas.map {
            executor.submitWithLogContext("provision $it") { it.provision() }
        }

        val databaseDataLocation = setupDatabase.get()

        val nodes = nodesProvisioning
            .map { it.get() }
            .map { node -> time("start $node") { node.start() } }
        executor.shutdownNow()

        time("wait for loadbalancer") {
            loadBalancer.waitUntilHealthy(Duration.ofMinutes(5))
        }

        val jira = Jira(
            nodes = nodes,
            jiraHome = RemoteLocation(
                sharedHomeSsh.host,
                sharedHome.get().remoteSharedHome
            ),
            database = databaseDataLocation,
            address = loadBalancer.uri,
            jmxClients = jiraNodes.mapIndexed { i, node -> configs[i].remoteJmx.getClient(node.publicIpAddress) }
        )
        logger.info("$jira is set up, will expire ${jiraStack.expiry}")
        return@time ProvisionedJira(
            jira = jira,
            resource = DependentResources(
                user = provisionedLoadBalancer.resource,
                dependency = jiraStack
            )
        )
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

        private var configs: List<JiraNodeConfig> = (1..2).map { JiraNodeConfig.Builder().name("jira-node-$it").build() }
        private var loadBalancerFormula: LoadBalancerFormula = ElasticLoadBalancerFormula()
        private var apps: Apps = Apps(emptyList())
        private var computer: Computer = C4EightExtraLargeElastic()
        private var stackCreationTimeout: Duration = Duration.ofMinutes(30)
        private var network: Network? = null
        private var databaseComputer: Computer = M4ExtraLargeElastic()

        internal constructor(
            formula: DataCenterFormula
        ) : this(
            productDistribution = formula.productDistribution,
            jiraHomeSource = formula.jiraHomeSource,
            database = formula.database
        ) {
            configs = formula.configs
            loadBalancerFormula = formula.loadBalancerFormula
            apps = formula.apps
            computer = formula.computer
            stackCreationTimeout = formula.stackCreationTimeout
            network = formula.overriddenNetwork
        }

        fun configs(configs: List<JiraNodeConfig>): Builder = apply { this.configs = configs }

        fun loadBalancerFormula(loadBalancerFormula: LoadBalancerFormula): Builder =
            apply { this.loadBalancerFormula = loadBalancerFormula }

        fun apps(apps: Apps): Builder = apply { this.apps = apps }

        fun computer(computer: Computer): Builder = apply { this.computer = computer }

        fun stackCreationTimeout(stackCreationTimeout: Duration): Builder =
            apply { this.stackCreationTimeout = stackCreationTimeout }

        fun databaseComputer(databaseComputer: Computer): Builder = apply { this.databaseComputer = databaseComputer }

        internal fun network(network: Network) = apply { this.network = network }

        fun build(): DataCenterFormula = DataCenterFormula(
            configs = configs,
            loadBalancerFormula = loadBalancerFormula,
            apps = apps,
            productDistribution = productDistribution,
            jiraHomeSource = jiraHomeSource,
            database = database,
            computer = computer,
            stackCreationTimeout = stackCreationTimeout,
            overriddenNetwork = network,
            databaseComputer = databaseComputer
        )
    }
}

