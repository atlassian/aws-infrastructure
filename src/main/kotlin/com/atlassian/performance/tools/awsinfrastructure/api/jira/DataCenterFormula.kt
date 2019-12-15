package com.atlassian.performance.tools.awsinfrastructure.api.jira

import com.amazonaws.services.cloudformation.model.Parameter
import com.atlassian.performance.tools.aws.api.*
import com.atlassian.performance.tools.awsinfrastructure.ApplicationStorageWrapper
import com.atlassian.performance.tools.awsinfrastructure.InstanceAddressSelector
import com.atlassian.performance.tools.awsinfrastructure.InstanceFilters
import com.atlassian.performance.tools.awsinfrastructure.TemplateBuilder
import com.atlassian.performance.tools.awsinfrastructure.api.RemoteLocation
import com.atlassian.performance.tools.awsinfrastructure.api.hardware.C4EightExtraLargeElastic
import com.atlassian.performance.tools.awsinfrastructure.api.hardware.Computer
import com.atlassian.performance.tools.awsinfrastructure.api.hardware.M4ExtraLargeElastic
import com.atlassian.performance.tools.awsinfrastructure.api.hardware.Volume
import com.atlassian.performance.tools.awsinfrastructure.api.loadbalancer.ApacheEc2LoadBalancerFormula
import com.atlassian.performance.tools.awsinfrastructure.api.loadbalancer.ApacheProxyLoadBalancer
import com.atlassian.performance.tools.awsinfrastructure.api.loadbalancer.LoadBalancerFormula
import com.atlassian.performance.tools.awsinfrastructure.api.network.Network
import com.atlassian.performance.tools.awsinfrastructure.api.network.NetworkFormula
import com.atlassian.performance.tools.awsinfrastructure.jira.DataCenterNodeFormula
import com.atlassian.performance.tools.awsinfrastructure.jira.DiagnosableNodeFormula
import com.atlassian.performance.tools.awsinfrastructure.jira.StandaloneNodeFormula
import com.atlassian.performance.tools.awsinfrastructure.jira.home.SharedHomeFormula
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
    private val jiraVolume: Volume,
    private val stackCreationTimeout: Duration,
    private val overriddenNetwork: Network? = null,
    private val databaseComputer: Computer,
    private val databaseVolume: Volume
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
        jiraVolume = Volume(100),
        stackCreationTimeout = Duration.ofMinutes(30),
        databaseComputer = M4ExtraLargeElastic(),
        databaseVolume = Volume(100)
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
        loadBalancerFormula = ApacheEc2LoadBalancerFormula(),
        apps = apps,
        productDistribution = ApplicationStorageWrapper(application),
        jiraHomeSource = jiraHomeSource,
        database = database,
        computer = C4EightExtraLargeElastic(),
        jiraVolume = Volume(100),
        stackCreationTimeout = Duration.ofMinutes(30),
        databaseComputer = M4ExtraLargeElastic(),
        databaseVolume = Volume(100)
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
        val jiraNodes = InstanceFilters().jiraInstances(machines)
        val databaseIp = InstanceAddressSelector.getReachableIpAddress(InstanceFilters().dbInstance(machines))
        val sharedHomeMachine = InstanceFilters().sharedHome(machines)
        val sharedHomeIp = InstanceAddressSelector.getReachableIpAddress(sharedHomeMachine)
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
            .onEach { instance ->
                CloseableThreadContext.push("a jira node").use {
                    key.get().file.facilitateSsh(InstanceAddressSelector.getReachableIpAddress(instance))
                }
            }
            .mapIndexed { i: Int, instance ->
                val ipAddress = InstanceAddressSelector.getReachableIpAddress(instance)
                val ssh = Ssh(SshHost(ipAddress, "ubuntu", keyPath), connectivityPatience = 5)
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
                        sharedHome = sharedHome,
                        privateIpAddress = instance.privateIpAddress
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

        val updateJiraConfiguration =
            if (loadBalancer is ApacheProxyLoadBalancer) listOf(loadBalancer::updateJiraConfiguration) else emptyList()

        val nodes = nodesProvisioning
            .map { it.get() }
            .map { node -> time("start $node") { node.start(updateJiraConfiguration) } }

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
            jmxClients = jiraNodes.mapIndexed { i, node -> configs[i].remoteJmx.getClient(InstanceAddressSelector.getReachableIpAddress(node)) }
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
        private var loadBalancerFormula: LoadBalancerFormula = ApacheEc2LoadBalancerFormula()
        private var apps: Apps = Apps(emptyList())
        private var computer: Computer = C4EightExtraLargeElastic()
        private var jiraVolume: Volume = Volume(100)
        private var stackCreationTimeout: Duration = Duration.ofMinutes(30)
        private var network: Network? = null
        private var databaseComputer: Computer = M4ExtraLargeElastic()
        private var databaseVolume: Volume = Volume(100)

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
            jiraVolume = formula.jiraVolume
            stackCreationTimeout = formula.stackCreationTimeout
            network = formula.overriddenNetwork
            databaseComputer = formula.databaseComputer
            databaseVolume = formula.databaseVolume
        }

        fun configs(configs: List<JiraNodeConfig>): Builder = apply { this.configs = configs }

        fun loadBalancerFormula(loadBalancerFormula: LoadBalancerFormula): Builder =
            apply { this.loadBalancerFormula = loadBalancerFormula }

        fun apps(apps: Apps): Builder = apply { this.apps = apps }

        fun computer(computer: Computer): Builder = apply { this.computer = computer }

        fun jiraVolume(jiraVolume: Volume): Builder = apply { this.jiraVolume = jiraVolume }

        fun stackCreationTimeout(stackCreationTimeout: Duration): Builder =
            apply { this.stackCreationTimeout = stackCreationTimeout }

        fun databaseComputer(databaseComputer: Computer): Builder = apply { this.databaseComputer = databaseComputer }

        fun databaseVolume(databaseVolume: Volume): Builder = apply { this.databaseVolume = databaseVolume }

        internal fun network(network: Network) = apply { this.network = network }

        fun build(): DataCenterFormula = DataCenterFormula(
            configs = configs,
            loadBalancerFormula = loadBalancerFormula,
            apps = apps,
            productDistribution = productDistribution,
            jiraHomeSource = jiraHomeSource,
            database = database,
            computer = computer,
            jiraVolume = jiraVolume,
            stackCreationTimeout = stackCreationTimeout,
            overriddenNetwork = network,
            databaseComputer = databaseComputer,
            databaseVolume = databaseVolume
        )
    }
}

