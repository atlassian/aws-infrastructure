package com.atlassian.performance.tools.awsinfrastructure.jira

import com.amazonaws.services.cloudformation.model.Parameter
import com.amazonaws.services.ec2.model.AvailabilityZone
import com.amazonaws.services.ec2.model.Tag
import com.atlassian.performance.tools.aws.*
import com.atlassian.performance.tools.awsinfrastructure.RemoteLocation
import com.atlassian.performance.tools.awsinfrastructure.TemplateBuilder
import com.atlassian.performance.tools.awsinfrastructure.jira.home.SharedHomeFormula
import com.atlassian.performance.tools.awsinfrastructure.loadbalancer.ElasticLoadBalancerFormula
import com.atlassian.performance.tools.awsinfrastructure.loadbalancer.LoadBalancerFormula
import com.atlassian.performance.tools.awsinfrastructure.storage.ApplicationStorage
import com.atlassian.performance.tools.concurrency.submitWithLogContext
import com.atlassian.performance.tools.infrastructure.Database
import com.atlassian.performance.tools.infrastructure.app.Apps
import com.atlassian.performance.tools.infrastructure.jira.home.JiraHomeSource
import com.atlassian.performance.tools.infrastructure.jira.nodes.JiraNodeConfig
import com.atlassian.performance.tools.jvmtasks.TaskTimer.time
import com.atlassian.performance.tools.ssh.Ssh
import com.atlassian.performance.tools.ssh.SshHost
import com.google.common.util.concurrent.ThreadFactoryBuilder
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.Future

/**
 * @param configs applied to nodes in the same order as they are provisioned and started
 */
class DataCenterFormula(
    private val configs: List<JiraNodeConfig> = JiraNodeConfig().clone(times = 2),
    private val loadBalancerFormula: LoadBalancerFormula = ElasticLoadBalancerFormula(),
    private val apps: Apps,
    private val application: ApplicationStorage,
    private val jiraHomeSource: JiraHomeSource,
    private val database: Database
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
            .map { it.publicIpAddress }
            .onEach { key.get().file.facilitateSsh(it) }
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
                            config = configs[i]
                        ),
                        nodeIndex = i,
                        sharedHome = sharedHome
                    )
                )
            }

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
        return ProvisionedJira(
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

