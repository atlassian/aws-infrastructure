package com.atlassian.performance.tools.awsinfrastructure.api.jira

import com.amazonaws.services.cloudformation.model.Parameter
import com.amazonaws.services.ec2.model.AvailabilityZone
import com.amazonaws.services.ec2.model.Tag
import com.atlassian.performance.tools.aws.api.*
import com.atlassian.performance.tools.awsinfrastructure.TemplateBuilder
import com.atlassian.performance.tools.awsinfrastructure.api.RemoteLocation
import com.atlassian.performance.tools.awsinfrastructure.api.hardware.C4EightExtraLargeElastic
import com.atlassian.performance.tools.awsinfrastructure.api.hardware.Computer
import com.atlassian.performance.tools.awsinfrastructure.api.loadbalancer.ElasticLoadBalancerFormula
import com.atlassian.performance.tools.awsinfrastructure.api.loadbalancer.LoadBalancerFormula
import com.atlassian.performance.tools.awsinfrastructure.api.storage.ApplicationStorage
import com.atlassian.performance.tools.awsinfrastructure.jira.DataCenterNodeFormula
import com.atlassian.performance.tools.awsinfrastructure.jira.DiagnosableNodeFormula
import com.atlassian.performance.tools.awsinfrastructure.jira.StandaloneNodeFormula
import com.atlassian.performance.tools.awsinfrastructure.jira.home.SharedHomeFormula
import com.atlassian.performance.tools.concurrency.api.submitWithLogContext
import com.atlassian.performance.tools.infrastructure.api.app.Apps
import com.atlassian.performance.tools.infrastructure.api.database.Database
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
 */
class DataCenterFormula(
    private val configs: List<JiraNodeConfig>,
    private val loadBalancerFormula: LoadBalancerFormula,
    private val apps: Apps,
    private val application: ApplicationStorage,
    private val jiraHomeSource: JiraHomeSource,
    private val database: Database,
    private val computer: Computer
) : JiraFormula {
    private val logger: Logger = LogManager.getLogger(this::class.java)

    @Deprecated(
        message = "Use the primary constructor"
    )
    constructor(
        configs: List<JiraNodeConfig> = JiraNodeConfig().clone(times = 2),
        loadBalancerFormula: LoadBalancerFormula = ElasticLoadBalancerFormula(),
        apps: Apps,
        application: ApplicationStorage,
        jiraHomeSource: JiraHomeSource,
        database: Database
    ) : this(
        configs = configs,
        loadBalancerFormula = loadBalancerFormula,
        apps = apps,
        application = application,
        jiraHomeSource = jiraHomeSource,
        database = database,
        computer = C4EightExtraLargeElastic()
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
                        .withParameterKey("AvailabilityZone")
                        .withParameterValue(pickAvailabilityZone(aws).zoneName)
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
        val jiraNodes = machines.filter { it.tags.contains(Tag("jpt-jira", "true")) }
        val databaseIp = machines.single { it.tags.contains(Tag("jpt-database", "true")) }.publicIpAddress
        val sharedHomeMachine = machines.single { it.tags.contains(Tag("jpt-shared-home", "true")) }
        val sharedHomeIp = sharedHomeMachine.publicIpAddress
        val sharedHomePrivateIp = sharedHomeMachine.privateIpAddress
        val sharedHomeSsh = Ssh(SshHost(sharedHomeIp, "ubuntu", keyPath))
        val futureLoadBalancer = executor.submitWithLogContext("provision load balancer") {
            loadBalancerFormula.provision(
                investment = investment,
                instances = jiraNodes,
                subnet = jiraStack.findSubnet("Subnet"),
                vpc = jiraStack.findVpc("VPC"),
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
                ssh = sharedHomeSsh
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
                            application = application,
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

    private fun pickAvailabilityZone(
        aws: Aws
    ): AvailabilityZone {
        val zone = aws
            .availabilityZones
            .filter { it.zoneName != "eu-central-1c" }
            .shuffled()
            .first()
        logger.debug("Picked $zone")
        return zone
    }
}

