package com.atlassian.performance.tools.awsinfrastructure.api.jira

import com.atlassian.performance.tools.aws.api.Investment
import com.atlassian.performance.tools.aws.api.SshKeyFormula
import com.atlassian.performance.tools.awsinfrastructure.IntegrationTestRuntime
import com.atlassian.performance.tools.awsinfrastructure.api.DatasetCatalogue
import com.atlassian.performance.tools.awsinfrastructure.api.hardware.C5NineExtraLargeEphemeral
import com.atlassian.performance.tools.awsinfrastructure.api.loadbalancer.ApacheEc2LoadBalancerFormula
import com.atlassian.performance.tools.concurrency.api.submitWithLogContext
import com.atlassian.performance.tools.infrastructure.api.dataset.Dataset
import com.atlassian.performance.tools.infrastructure.api.distribution.PublicJiraSoftwareDistribution
import com.atlassian.performance.tools.infrastructure.api.jira.JiraNodeConfig
import com.atlassian.performance.tools.infrastructure.api.jvm.jmx.EnabledRemoteJmx
import com.atlassian.performance.tools.workspace.api.TaskWorkspace
import com.atlassian.performance.tools.workspace.api.TestWorkspace
import com.google.common.util.concurrent.ThreadFactoryBuilder
import org.apache.logging.log4j.CloseableThreadContext
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Percentage
import org.junit.Test
import java.net.URI
import java.nio.file.Files
import java.time.Duration
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class DataCenterFormulaIT {
    private val workspace = IntegrationTestRuntime.taskWorkspace
    private val aws = IntegrationTestRuntime.aws
    private val jiraVersionSeven = "7.2.0"
    private val jiraVersionEight = "8.22.0"
    private val datasetSeven = DatasetCatalogue().smallJiraSeven()
    private val datasetEight = DatasetCatalogue().largeJiraEight()

    @Test
    fun shouldProvisionDataCenter() {
        val executor = Executors.newFixedThreadPool(
            3,
            ThreadFactoryBuilder()
                .setNameFormat("DCFIT-test-thread-%d")
                .build()
        )
        listOf(
            DataCenterProvisioningTest(
                jiraVersion = jiraVersionSeven,
                dataset = datasetSeven
            ),
            DataCenterJmxProvisioningTest(
                jiraVersion = jiraVersionEight,
                dataset = datasetEight
            ),
            ShouldGatherApacheLogsFromDataCenter(
                jiraVersion = jiraVersionEight,
                dataset = datasetEight
            )
        )
            .map { test ->
                executor.submitWithLogContext(test.jiraVersion) {
                    test.run(
                        group = "DCFIT-test",
                        workspace = workspace
                    )
                }
            }
            .forEach { it.get() }
        executor.shutdownNow()
    }

    private inner class DataCenterProvisioningTest(
        override val jiraVersion: String,
        private val dataset: Dataset
    ) : GroupableTest("Data Center provisioning - Jira version $jiraVersion") {
        override fun run(workspace: TestWorkspace) {
            val nonce = UUID.randomUUID().toString()
            val lifespan = Duration.ofMinutes(30)
            val keyFormula = SshKeyFormula(
                ec2 = aws.ec2,
                workingDirectory = workspace.directory,
                lifespan = lifespan,
                prefix = nonce
            )
            val dcFormula = DataCenterFormula.Builder(
                productDistribution = PublicJiraSoftwareDistribution(jiraVersion),
                jiraHomeSource = dataset.jiraHomeSource,
                database = dataset.database
            ).computer(C5NineExtraLargeEphemeral())
                .databaseComputer(C5NineExtraLargeEphemeral())
                .build()

            val provisionedJira = dcFormula.provision(
                investment = Investment(
                    useCase = "Test Data Center provisioning",
                    lifespan = lifespan
                ),
                pluginsTransport = aws.jiraStorage(nonce),
                resultsTransport = aws.resultsStorage(nonce),
                key = CompletableFuture.completedFuture(keyFormula.provision()),
                roleProfile = aws.shortTermStorageAccess(),
                aws = aws
            )

            runLoadBalancerTest(provisionedJira.jira.address)

            provisionedJira.resource.release().get(3, TimeUnit.MINUTES)
        }

        private fun runLoadBalancerTest(address: URI) {
            val executor = Executors.newFixedThreadPool(
                20,
                ThreadFactoryBuilder()
                    .setNameFormat("DCFIT-test-load-balancer-thread-%d")
                    .build()
            )

            val routeIds = (1..1000).map {
                executor.submitWithLogContext("Test") {
                    getRouteId(address)
                }
            }
                .map { it.get() }
                .groupingBy { it }
                .eachCount()

            assertThat(routeIds.size).isEqualTo(2)

            val counts = routeIds.entries.map { it.value }

            assertThat(counts[0]).isCloseTo(counts[1], Percentage.withPercentage(10.0))
        }

        private fun getRouteId(address: URI): String {
            val cookiesList = address.toURL().openConnection().headerFields["Set-Cookie"]

            assertThat(cookiesList).isNotNull

            val routeId = cookiesList!!.filter { str ->
                str.contains("ROUTEID")
            }

            assertThat(routeId.size).isEqualTo(1)

            return routeId[0].split(";")[0]
        }
    }

    private inner class DataCenterJmxProvisioningTest(
        override val jiraVersion: String,
        private val dataset: Dataset
    ) : GroupableTest("Data Center with JMX provisioning - Jira version $jiraVersion") {
        override fun run(workspace: TestWorkspace) {
            val nonce = UUID.randomUUID().toString()
            val lifespan = Duration.ofMinutes(30)
            val keyFormula = SshKeyFormula(
                ec2 = aws.ec2,
                workingDirectory = workspace.directory,
                lifespan = lifespan,
                prefix = nonce
            )
            val config = JiraNodeConfig.Builder()
                .remoteJmx(EnabledRemoteJmx())
                .build()
            val dcFormula = DataCenterFormula.Builder(
                productDistribution = PublicJiraSoftwareDistribution(jiraVersion),
                jiraHomeSource = dataset.jiraHomeSource,
                database = dataset.database
            ).computer(C5NineExtraLargeEphemeral())
                .databaseComputer(C5NineExtraLargeEphemeral())
                .configs(
                    (1..2).map {
                        JiraNodeConfig.Builder(config)
                            .name("${config.name}-$it")
                            .build()
                    }
                )
                .build()

            val provisionedJira = dcFormula.provision(
                investment = Investment(
                    useCase = "Test Data Center provisioning",
                    lifespan = lifespan
                ),
                pluginsTransport = aws.jiraStorage(nonce),
                resultsTransport = aws.resultsStorage(nonce),
                key = CompletableFuture.completedFuture(keyFormula.provision()),
                roleProfile = aws.shortTermStorageAccess(),
                aws = aws
            )
            val resource = provisionedJira.resource

            provisionedJira.jira.jmxClients.forEach { client ->
                client.execute { connector -> assertThat(connector.mBeanServerConnection.mBeanCount).isGreaterThan(0) }
            }

            resource.release().get(3, TimeUnit.MINUTES)
        }
    }

    private inner class ShouldGatherApacheLogsFromDataCenter(
        override val jiraVersion: String,
        private val dataset: Dataset
    ) : GroupableTest("Should gather Apache logs from Data Center") {
        override fun run(workspace: TestWorkspace) {
            val nonce = UUID.randomUUID().toString()
            val lifespan = Duration.ofMinutes(30)
            val keyFormula = SshKeyFormula(
                ec2 = aws.ec2,
                workingDirectory = workspace.directory,
                lifespan = lifespan,
                prefix = nonce
            )
            val dcFormula = DataCenterFormula.Builder(
                productDistribution = PublicJiraSoftwareDistribution(jiraVersion),
                jiraHomeSource = dataset.jiraHomeSource,
                database = dataset.database
            ).computer(C5NineExtraLargeEphemeral())
                .databaseComputer(C5NineExtraLargeEphemeral())
                .loadBalancerFormula(ApacheEc2LoadBalancerFormula())
                .build()

            val provisionedJira = dcFormula.provision(
                investment = Investment(
                    useCase = "Test Data Center provisioning",
                    lifespan = lifespan
                ),
                pluginsTransport = aws.jiraStorage(nonce),
                resultsTransport = aws.resultsStorage(nonce),
                key = CompletableFuture.completedFuture(keyFormula.provision()),
                roleProfile = aws.shortTermStorageAccess(),
                aws = aws
            )
            provisionedJira.jira.gatherResults()

            assertThat(Files.walk(workspace.directory)).anyMatch {
                it.parent.endsWith("apache2") && it.fileName.toString().endsWith(".log")
            }

            provisionedJira.resource.release().get(3, TimeUnit.MINUTES)
        }
    }

    private abstract class GroupableTest(
        protected val feature: String
    ) {
        abstract val jiraVersion: String

        fun run(
            group: String,
            workspace: TaskWorkspace
        ) {
            CloseableThreadContext.put("test", "$group : $feature").use {
                run(workspace.isolateTest("$group - $feature"))
            }
        }

        abstract fun run(workspace: TestWorkspace)
    }
}