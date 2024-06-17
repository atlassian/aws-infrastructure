package com.atlassian.performance.tools.awsinfrastructure.api.jira

import com.atlassian.performance.tools.aws.api.Investment
import com.atlassian.performance.tools.aws.api.SshKeyFormula
import com.atlassian.performance.tools.awsinfrastructure.IntegrationTestRuntime.aws
import com.atlassian.performance.tools.awsinfrastructure.IntegrationTestRuntime.taskWorkspace
import com.atlassian.performance.tools.awsinfrastructure.api.DatasetCatalogue
import com.atlassian.performance.tools.awsinfrastructure.api.hardware.C5NineExtraLargeEphemeral
import com.atlassian.performance.tools.awsinfrastructure.api.hardware.Volume
import com.atlassian.performance.tools.infrastructure.api.database.MinimalMysqlDatabase
import com.atlassian.performance.tools.infrastructure.api.distribution.PublicJiraServiceDeskDistribution
import com.atlassian.performance.tools.infrastructure.api.distribution.PublicJiraSoftwareDistribution
import com.atlassian.performance.tools.infrastructure.api.jira.JiraNodeConfig
import com.atlassian.performance.tools.infrastructure.api.jira.MinimalMysqlJiraHome
import com.atlassian.performance.tools.infrastructure.api.jvm.OpenJDK
import org.assertj.core.api.Assertions
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URI
import java.time.Duration
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class StandaloneFormulaIT {

    private val workspace = taskWorkspace.isolateTest(javaClass.simpleName)
    private val dataset = DatasetCatalogue().smallJiraSeven()

    /**
     * The default JDK in [JiraNodeConfig] is flaky to install.
     */
    private val stableJdk = JiraNodeConfig.Builder()
        .versionedJdk(OpenJDK())
        .build()

    @Test
    fun shouldProvisionServer() {
        val nonce = UUID.randomUUID().toString()
        val lifespan = Duration.ofMinutes(30)
        val keyFormula = SshKeyFormula(
            ec2 = aws.ec2,
            workingDirectory = workspace.directory,
            lifespan = lifespan,
            prefix = nonce
        )
        val serverFormula = StandaloneFormula.Builder(
            productDistribution = PublicJiraServiceDeskDistribution("3.9.8"),
            database = dataset.database,
            jiraHomeSource = dataset.jiraHomeSource
        ).computer(C5NineExtraLargeEphemeral())
            .jiraVolume(Volume(80))
            .databaseComputer(C5NineExtraLargeEphemeral())
            .databaseVolume(Volume(90))
            .config(stableJdk)
            .build()
        val copiedFormula = StandaloneFormula.Builder(serverFormula).build()

        val resource = copiedFormula.provision(
            investment = Investment(
                useCase = "Test JSD Server provisioning",
                lifespan = lifespan
            ),
            pluginsTransport = aws.jiraStorage(nonce),
            resultsTransport = aws.resultsStorage(nonce),
            key = CompletableFuture.completedFuture(keyFormula.provision()),
            roleProfile = aws.shortTermStorageAccess(),
            aws = aws
        ).resource

        resource.release().get(1, TimeUnit.MINUTES)
    }

    @Test
    fun shouldProvisionJiraWithMinimalDataset() {
        val distribution = PublicJiraSoftwareDistribution("9.1.0")
        val jiraHome = MinimalMysqlJiraHome()
        val database = MinimalMysqlDatabase.Builder().build()
        val jiraFormula = StandaloneFormula.Builder(distribution, jiraHome, database)
            .config(stableJdk)
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
            .map { it.also { if (it != 200) Thread.sleep(Duration.ofSeconds(15).toMillis()) } }
            .take(20)
            .find { it == 200 }
        resource.release().get(3, TimeUnit.MINUTES)

        Assertions.assertThat(response).isEqualTo(200)
    }

    private fun URI.queryHttp() = toURL().openConnection()
        .let { it as? HttpURLConnection }
        ?.let {
            try { it.responseCode }
            catch (e: Exception) { -1 }
            finally { it.disconnect() }
        }
}