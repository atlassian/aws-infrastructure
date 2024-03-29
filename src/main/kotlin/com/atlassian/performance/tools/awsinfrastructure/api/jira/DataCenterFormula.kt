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
import com.atlassian.performance.tools.awsinfrastructure.api.loadbalancer.ApacheEc2LoadBalancerFormula
import com.atlassian.performance.tools.awsinfrastructure.api.loadbalancer.ApacheProxyLoadBalancer
import com.atlassian.performance.tools.awsinfrastructure.api.loadbalancer.LoadBalancerFormula
import com.atlassian.performance.tools.awsinfrastructure.api.network.Network
import com.atlassian.performance.tools.awsinfrastructure.api.network.NetworkFormula
import com.atlassian.performance.tools.awsinfrastructure.api.network.access.*
import com.atlassian.performance.tools.awsinfrastructure.jira.DataCenterNodeFormula
import com.atlassian.performance.tools.awsinfrastructure.jira.DiagnosableNodeFormula
import com.atlassian.performance.tools.awsinfrastructure.jira.StandaloneNodeFormula
import com.atlassian.performance.tools.awsinfrastructure.jira.home.SharedHomeFormula
import com.atlassian.performance.tools.concurrency.api.AbruptExecutorService
import com.atlassian.performance.tools.concurrency.api.submitWithLogContext
import com.atlassian.performance.tools.infrastructure.api.app.Apps
import com.atlassian.performance.tools.infrastructure.api.database.Database
import com.atlassian.performance.tools.infrastructure.api.distribution.ProductDistribution
import com.atlassian.performance.tools.infrastructure.api.jira.JiraHomeSource
import com.atlassian.performance.tools.infrastructure.api.jira.JiraNodeConfig
import com.atlassian.performance.tools.jvmtasks.api.EventBus
import com.atlassian.performance.tools.jvmtasks.api.TaskScope.task
import com.atlassian.performance.tools.ssh.api.Ssh
import com.atlassian.performance.tools.ssh.api.SshHost
import com.google.common.util.concurrent.ThreadFactoryBuilder
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.time.Duration
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.function.Supplier

/**
 * The EC2 instances provisioned with this class will have 'instance initiated shutdown' parameter set to 'terminate'.
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
            Executors.newCachedThreadPool(
                ThreadFactoryBuilder()
                    .setNameFormat("data-center-provisioning-thread-%d")
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
        val databaseMachine = InstanceFilters().dbInstance(machines)
        val sharedHomeMachine = InstanceFilters().sharedHome(machines)
        val sharedHomeSshHost = SshHost(sharedHomeMachine.publicIpAddress, "ubuntu", keyPath)

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

        // shared home provisioning relies on plugins being uploaded
        uploadPlugins.get()
        val sharedHome = executor.submitWithLogContext("provision shared home") {
            EventBus.publish(sharedHomeMachine)
            logger.info("Setting up shared home...")
            val sharedHomeSsh = Ssh(sharedHomeSshHost, connectivityPatience = 4)
            key.get().file.facilitateSsh(sharedHomeMachine.publicIpAddress)
            val sharedHome = SharedHomeFormula(
                jiraHomeSource = jiraHomeSource,
                pluginsTransport = pluginsTransport,
                ip = sharedHomeMachine.privateIpAddress,
                ssh = sharedHomeSsh,
                computer = computer
            ).provision()
            logger.info("Shared home is set up")
            sharedHome
        }

        val nodesProvisioning = jiraNodes.mapIndexed { i: Int, instance ->
            val nodeConfig = configs[i]
            executor.submitWithLogContext("install ${nodeConfig.name}") {
                EventBus.publish(instance)
                val sshIpAddress = instance.publicIpAddress
                val ssh = Ssh(SshHost(sshIpAddress, "ubuntu", keyPath), connectivityPatience = 5)
                key.get().file.facilitateSsh(sshIpAddress)
                val baseNode = StandaloneNodeFormula(
                    resultsTransport = resultsTransport,
                    databaseIp = databaseMachine.privateIpAddress,
                    jiraHomeSource = jiraHomeSource,
                    pluginsTransport = pluginsTransport,
                    productDistribution = productDistribution,
                    ssh = ssh,
                    waitForUpgrades = waitForUpgrades,
                    config = nodeConfig,
                    computer = computer,
                    adminPasswordPlainText = adminPasswordPlainText
                )
                val dcNode = DataCenterNodeFormula(
                    base = baseNode,
                    sharedHome = sharedHome,
                    privateIpAddress = instance.privateIpAddress
                )
                DiagnosableNodeFormula(dcNode).provision()
            }
        }

        val provisionedLoadBalancer = futureLoadBalancer.get()
        val loadBalancer = provisionedLoadBalancer.loadBalancer

        val jiraNodeSecurityGroup = jiraStack.findSecurityGroup("JiraNodeSecurityGroup")
        val jiraNodeHttpAccessProvider = SecurityGroupIngressAccessProvider
            .Builder(ec2 = aws.ec2, securityGroup = jiraNodeSecurityGroup, portRange = 8080..8080).build()
        val jiraNodeJvmDebugAccessProvider = MultiAccessProvider(
            configs.flatMap { it.debug.getRequiredPorts() }.toSet().map {
                SecurityGroupIngressAccessProvider
                    .Builder(ec2 = aws.ec2, securityGroup = jiraNodeSecurityGroup, portRange = it..it).build()
            }
        )
        val jiraNodeJmxAccessProvider = MultiAccessProvider(
            configs.flatMap { it.remoteJmx.getRequiredPorts() }.toSet().map {
                SecurityGroupIngressAccessProvider
                    .Builder(ec2 = aws.ec2, securityGroup = jiraNodeSecurityGroup, portRange = it..it).build()
            }
        )
        val jiraNodeSplunkForwarderAccessProvider = MultiAccessProvider(
            configs.flatMap { it.splunkForwarder.getRequiredPorts() }.toSet().map {
                SecurityGroupIngressAccessProvider
                    .Builder(ec2 = aws.ec2, securityGroup = jiraNodeSecurityGroup, portRange = it..it).build()
            }
        )
        val jiraNodeRmiAccessProvider = MultiAccessProvider(
            setOf(40001, 40011).map {
                SecurityGroupIngressAccessProvider
                    .Builder(ec2 = aws.ec2, securityGroup = jiraNodeSecurityGroup, portRange = it..it).build()
            }
        )
        val jiraAccessProvider = MultiAccessProvider(
            listOf(
                provisionedLoadBalancer.accessProvider,
                jiraNodeHttpAccessProvider,
                jiraNodeJvmDebugAccessProvider,
                jiraNodeJmxAccessProvider,
                jiraNodeSplunkForwarderAccessProvider
            )
        )

        // When JMX is enabled without granting ehcache RMI port access to *PUBLIC* IP addresses of Jira nodes,
        // it takes longer for Jira DC to start. It's unknown why exactly is that.
        val rmiNodePublicAccess = executor.submitWithLogContext("rmi node public access") {
            MultiAccessRequester(jiraNodes.map { ForIpAccessRequester(Supplier { it.publicIpAddress }) })
                .requestAccess(jiraNodeRmiAccessProvider)
        }
        val selfDashboardAccess = executor.submitWithLogContext("self dashboard access") {
            MultiAccessRequester(jiraNodes.map { ForIpAccessRequester(Supplier { it.publicIpAddress }) })
                .requestAccess(provisionedLoadBalancer.accessProvider)
        }
        val loadBalancerAccess = executor.submitWithLogContext("load balancer access") {
            provisionedLoadBalancer.accessRequester.requestAccess(jiraNodeHttpAccessProvider)
        }
        val externalAccess = executor.submitWithLogContext("external access") {
            accessRequester.requestAccess(jiraAccessProvider)
        }

        val setupDatabase = executor.submitWithLogContext("database") {
            EventBus.publish(databaseMachine)
            val databaseSshIp = databaseMachine.publicIpAddress
            val databaseSsh = Ssh(SshHost(databaseSshIp, "ubuntu", keyPath), connectivityPatience = 5)
            key.get().file.facilitateSsh(databaseSshIp)
            databaseSsh.newConnection().use {
                databaseComputer.setUp(it)
                logger.info("Setting up database...")
                val databaseDataLocation = database.setup(it)
                logger.info("Database is set up")
                logger.info("Starting database...")
                database.start(loadBalancer.uri, it)
                logger.info("Database is started")
                RemoteLocation(databaseSsh.host, databaseDataLocation)
            }
        }

        val databaseDataLocation = setupDatabase.get()

        val updateJiraConfiguration =
            if (loadBalancer is ApacheProxyLoadBalancer) listOf(loadBalancer::updateJiraConfiguration) else emptyList()

        // official docs require nodes to be added one by one: https://confluence.atlassian.com/adminjiraserver/installing-jira-data-center-938846870.html
        val nodes = nodesProvisioning
            .map { it.get() }
            .map { node -> task("start $node", Callable { node.start(updateJiraConfiguration) }) }

        if (!rmiNodePublicAccess.get()) {
            logger.warn("Jira nodes may not have access to other nodes RMI ports. This can cause slow Jira startup.")
        }
        if (!selfDashboardAccess.get()) {
            logger.warn("It's possible that Jira nodes don't have HTTP access to the load balancer. Dashboards may not work.")
        }
        if (!loadBalancerAccess.get()) {
            logger.warn("Load balancer may not have access to Jira nodes")
        }
        if (!externalAccess.get()) {
            logger.warn("It's possible that defined external access to Jira resources (e.g. http, debug, splunk) wasn't granted.")
        }

        executor.shutdownNow()

        task("wait for loadbalancer", Callable {
            loadBalancer.waitUntilHealthy(Duration.ofMinutes(5))
        })

        val jira = Jira.Builder(
            nodes = nodes,
            jiraHome = RemoteLocation(
                sharedHomeSshHost,
                sharedHome.get().remoteSharedHome
            ),
            database = databaseDataLocation,
            address = loadBalancer.uri
        )
            .jmxClients(jiraNodes.mapIndexed { i, node -> configs[i].remoteJmx.getClient(node.publicIpAddress) })
            .build()

        logger.info("$jira is set up, will expire ${jiraStack.expiry}")
        return ProvisionedJira.Builder(jira)
            .resource(
                DependentResources(
                    user = provisionedLoadBalancer.resource,
                    dependency = jiraStack
                ).let { instances ->
                    DependentResources(
                        user = instances,
                        dependency = provisionedNetwork.resource
                    )
                }
            )
            .accessProvider(jiraAccessProvider)
            .build()
    }

    class Builder(
        private val productDistribution: ProductDistribution,
        private val jiraHomeSource: JiraHomeSource,
        private val database: Database
    ) {
        private var configs: List<JiraNodeConfig> =
            (1..2).map { JiraNodeConfig.Builder().name("jira-node-$it").build() }
        private var loadBalancerFormula: LoadBalancerFormula = ApacheEc2LoadBalancerFormula()
        private var apps: Apps = Apps(emptyList())
        private var computer: Computer = C4EightExtraLargeElastic()
        private var jiraVolume: Volume = Volume(100)
        private var stackCreationTimeout: Duration = Duration.ofMinutes(30)
        private var network: Network? = null
        private var databaseComputer: Computer = M4ExtraLargeElastic()
        private var databaseVolume: Volume = Volume(100)
        private var accessRequester: AccessRequester = ForIpAccessRequester(LocalPublicIpv4Provider.Builder().build())
        private var adminPasswordPlainText: String = "admin"
        private var waitForUpgrades: Boolean = true

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
            accessRequester = formula.accessRequester
            adminPasswordPlainText = formula.adminPasswordPlainText
            waitForUpgrades = formula.waitForUpgrades
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

        fun adminPasswordPlainText(adminPasswordPlainText: String): Builder =
            apply { this.adminPasswordPlainText = adminPasswordPlainText }

        internal fun network(network: Network) = apply { this.network = network }

        fun accessRequester(accessRequester: AccessRequester) = apply { this.accessRequester = accessRequester }

        /**
         * Don't change when starting up multi-node Jira DC of version lower than 9.1.0
         * See https://confluence.atlassian.com/jirakb/index-management-on-jira-start-up-1141500654.html for more details.
         */
        fun waitForUpgrades(waitForUpgrades: Boolean) = apply { this.waitForUpgrades = waitForUpgrades }

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
            databaseVolume = databaseVolume,
            accessRequester = accessRequester,
            adminPasswordPlainText = adminPasswordPlainText,
            waitForUpgrades = waitForUpgrades
        )
    }
}

