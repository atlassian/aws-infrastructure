package com.atlassian.performance.tools.awsinfrastructure.api.jira

import com.atlassian.performance.tools.aws.api.Aws
import com.atlassian.performance.tools.aws.api.Investment
import com.atlassian.performance.tools.aws.api.SshKey
import com.atlassian.performance.tools.aws.api.SshKeyFormula
import com.atlassian.performance.tools.awsinfrastructure.IntegrationTestRuntime
import com.atlassian.performance.tools.awsinfrastructure.NetworkFormula
import com.atlassian.performance.tools.awsinfrastructure.api.Network
import com.atlassian.performance.tools.awsinfrastructure.api.database.AsyncInstallHook
import com.atlassian.performance.tools.awsinfrastructure.api.database.AwsSshMysql
import com.atlassian.performance.tools.awsinfrastructure.api.hardware.M5ExtraLargeEphemeral
import com.atlassian.performance.tools.awsinfrastructure.api.hardware.Volume
import com.atlassian.performance.tools.infrastructure.api.database.MySqlDatabase
import com.atlassian.performance.tools.infrastructure.api.dataset.Dataset
import com.atlassian.performance.tools.infrastructure.api.dataset.HttpDatasetPackage
import com.atlassian.performance.tools.infrastructure.api.jira.JiraHomePackage
import com.atlassian.performance.tools.infrastructure.api.jira.hook.JiraNodeHooks
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.net.URI
import java.nio.file.Files
import java.time.Duration
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class HooksServerFormulaIT {

    private val workspace = IntegrationTestRuntime.taskWorkspace.isolateTest(javaClass.simpleName)
    private val dataset = URI("https://s3-eu-west-1.amazonaws.com/")
        .resolve("jpt-custom-datasets-storage-a008820-datasetbucket-1sjxdtrv5hdhj/")
        .resolve("dataset-f8dba866-9d1b-492e-b76c-f4a78ac3958c/")
        .let { uri ->
            Dataset(
                label = "7k issues JSW 7.2.0",
                database = MySqlDatabase(HttpDatasetPackage(
                    uri = uri.resolve("database.tar.bz2"),
                    downloadTimeout = Duration.ofMinutes(6)
                )),
                jiraHomeSource = JiraHomePackage(HttpDatasetPackage(
                    uri = uri.resolve("jirahome.tar.bz2"),
                    downloadTimeout = Duration.ofMinutes(6)
                ))
            )
        }
    private val lifespan = Duration.ofMinutes(30)

    @Test
    fun shouldProvisionServer() {
        val aws = IntegrationTestRuntime.aws
        val nonce = UUID.randomUUID().toString()
        val (investment, sshKey, network) = provisionDependencies(aws, nonce)
        val mysqlHook = AsyncInstallHook(
            AwsSshMysql(
                dataset.database,
                aws,
                investment,
                M5ExtraLargeEphemeral(),
                Volume(100),
                network,
                sshKey,
                Duration.ofMinutes(4)
            ) // TODO builder
        )
        val serverFormula = HooksServerFormula.Builder()
            .node(
                JiraNodeProvisioning.Builder(dataset.jiraHomeSource)
                    .hooks(JiraNodeHooks.default().also { it.hook(mysqlHook) })
                    .build()
            )
            .network(network)
            .build()
        val resultsTransport = aws.resultsStorage(nonce)

        val provisionedJira = serverFormula.provision(
            investment = investment,
            pluginsTransport = aws.jiraStorage(nonce),
            resultsTransport = resultsTransport,
            key = CompletableFuture.completedFuture(sshKey),
            roleProfile = aws.shortTermStorageAccess(),
            aws = aws
        )
        provisionedJira.jira.gatherResults()

        val results = resultsTransport
            .download(Files.createTempDirectory("hooks-server-formula"))
            .toFile()
            .listFiles()
            ?.map { it.name }
        provisionedJira.resource.release().get(1, TimeUnit.MINUTES)
        assertThat(results).contains(
            "atlassian-jira.log",
            "catalina.out",
            "jpt-iostat.log",
            "jpt-jstat.log",
            "jpt-vmstat.log"
        )
    }

    private fun provisionDependencies(
        aws: Aws,
        nonce: String
    ): AwsServerDependencies {
        val sshKey = SshKeyFormula(
            ec2 = aws.ec2,
            workingDirectory = workspace.directory,
            lifespan = lifespan,
            prefix = nonce
        ).provision()
        val investment = Investment(
            useCase = "Test Server provisioning hook API",
            lifespan = lifespan
        )
        val network = NetworkFormula(investment, aws).provision()
        return AwsServerDependencies(investment, sshKey, network)
    }

    private data class AwsServerDependencies(
        val investment: Investment,
        val sshKey: SshKey,
        val network: Network
    )
}