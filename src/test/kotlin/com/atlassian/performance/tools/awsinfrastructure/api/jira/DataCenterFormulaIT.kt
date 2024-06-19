package com.atlassian.performance.tools.awsinfrastructure.api.jira

import com.atlassian.performance.tools.aws.api.Investment
import com.atlassian.performance.tools.aws.api.SshKeyFormula
import com.atlassian.performance.tools.awsinfrastructure.IntegrationTestRuntime
import com.atlassian.performance.tools.awsinfrastructure.api.DatasetCatalogue
import com.atlassian.performance.tools.awsinfrastructure.api.hardware.C5NineExtraLargeEphemeral
import com.atlassian.performance.tools.concurrency.api.submitWithLogContext
import com.atlassian.performance.tools.infrastructure.api.database.MinimalMysqlDatabase
import com.atlassian.performance.tools.infrastructure.api.distribution.PublicJiraSoftwareDistribution
import com.atlassian.performance.tools.infrastructure.api.jira.JiraNodeConfig
import com.atlassian.performance.tools.infrastructure.api.jira.MinimalMysqlJiraHome
import com.atlassian.performance.tools.infrastructure.api.jvm.OpenJDK
import com.atlassian.performance.tools.infrastructure.api.jvm.jmx.EnabledRemoteJmx
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Percentage.withPercentage
import org.junit.Test
import java.lang.Thread.sleep
import java.net.HttpURLConnection
import java.net.URI
import java.time.Duration
import java.time.Duration.ofSeconds
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors.newFixedThreadPool
import java.util.concurrent.TimeUnit

class DataCenterFormulaIT {
    private val workspace = IntegrationTestRuntime.taskWorkspace
    private val aws = IntegrationTestRuntime.aws

    /**
     * The default JDK in [JiraNodeConfig] is flaky to install.
     */
    private val stableJdk = JiraNodeConfig.Builder()
        .versionedJdk(OpenJDK())
        .build()

    @Test
    fun shouldProvisionDataCenter() {
        val dataset = DatasetCatalogue().largeJiraEight()
        val jiraVersion = "8.22.0"
        val testWorkspace = workspace.isolateTest("shouldProvisionDataCenter")
        val nonce = UUID.randomUUID().toString()
        val lifespan = Duration.ofMinutes(30)
        val keyFormula = SshKeyFormula(
            ec2 = aws.ec2,
            workingDirectory = testWorkspace.directory,
            lifespan = lifespan,
            prefix = nonce
        )
        val nodeConfigs = JiraNodeConfig.Builder(stableJdk)
            .remoteJmx(EnabledRemoteJmx())
            .build()
            .multipleNodes(2)
        val dcFormula = DataCenterFormula.Builder(
            productDistribution = PublicJiraSoftwareDistribution(jiraVersion),
            jiraHomeSource = dataset.jiraHomeSource,
            database = dataset.database
        ).computer(C5NineExtraLargeEphemeral())
            .databaseComputer(C5NineExtraLargeEphemeral())
            .waitForRunning(true)
            .configs(nodeConfigs)
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
        provisionedJira.jira.jmxClients.forEach { client ->
            client.execute { connector -> assertThat(connector.mBeanServerConnection.mBeanCount).isGreaterThan(0) }
        }

        provisionedJira.resource.release().get(3, TimeUnit.MINUTES)
    }

    private fun runLoadBalancerTest(address: URI) {
        val executor = newFixedThreadPool(20) { Thread(it, "DCFIT-test-load-balancer-thread-$it") }

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
        assertThat(counts[0]).isCloseTo(counts[1], withPercentage(10.0))
    }

    private fun getRouteId(address: URI): String {
        val cookiesList = address.toURL().openConnection().headerFields["Set-Cookie"]
        assertThat(cookiesList).isNotNull()
        val routeId = cookiesList!!.filter { str ->
            str.contains("ROUTEID")
        }
        assertThat(routeId).hasSize(1)
        return routeId[0].split(";")[0]
    }

    @Test
    fun shouldProvisionJiraWithMinimalDataset() {
        val distribution = PublicJiraSoftwareDistribution("9.1.0")
        val jiraHome = MinimalMysqlJiraHome()
        val database = MinimalMysqlDatabase.Builder().build()
        val jiraFormula = DataCenterFormula.Builder(distribution, jiraHome, database)
            .configs(listOf(stableJdk))
            .waitForUpgrades(false)
            .build()
        val investment = Investment(
            useCase = "Test if Jira provisions with minimal database and jirahome",
            lifespan = Duration.ofMinutes(30)
        )
        val nonce = investment.reuseKey()
        val keyFormula = SshKeyFormula(aws.ec2, workspace.directory, nonce, investment.lifespan)

        val provisionedJira = jiraFormula
            .provision(
                investment = investment,
                pluginsTransport = aws.jiraStorage(nonce),
                resultsTransport = aws.resultsStorage(nonce),
                key = CompletableFuture.completedFuture(keyFormula.provision()),
                roleProfile = aws.shortTermStorageAccess(),
                aws = aws
            )
        val resource = provisionedJira.resource
        val response = generateSequence { provisionedJira.jira.address.queryHttp() }
            .map { it.also { if (it != 200) sleep(ofSeconds(15).toMillis()) } }
            .take(20)
            .find { it == 200 }
        resource.release().get(3, TimeUnit.MINUTES)

        assertThat(response).isEqualTo(200)
    }

    private fun URI.queryHttp() = toURL().openConnection()
        .let { it as? HttpURLConnection }
        ?.let {
            try {
                it.responseCode
            } catch (e: Exception) {
                -1
            } finally {
                it.disconnect()
            }
        }
}

private fun JiraNodeConfig.multipleNodes(nodeCount: Int): List<JiraNodeConfig> {
    return (1..nodeCount).map { nodeIndex ->
        JiraNodeConfig.Builder(this)
            .name("${this.name}-$nodeIndex")
            .build()
    }
}