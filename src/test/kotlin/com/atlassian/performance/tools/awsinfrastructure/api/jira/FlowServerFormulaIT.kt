package com.atlassian.performance.tools.awsinfrastructure.api.jira

import com.atlassian.performance.tools.aws.api.Aws
import com.atlassian.performance.tools.aws.api.Investment
import com.atlassian.performance.tools.aws.api.SshKey
import com.atlassian.performance.tools.aws.api.SshKeyFormula
import com.atlassian.performance.tools.awsinfrastructure.IntegrationTestRuntime
import com.atlassian.performance.tools.awsinfrastructure.NetworkFormula
import com.atlassian.performance.tools.awsinfrastructure.api.Network
import com.atlassian.performance.tools.awsinfrastructure.api.database.AsyncTcpServerHook
import com.atlassian.performance.tools.awsinfrastructure.api.database.AwsSshMysql
import com.atlassian.performance.tools.awsinfrastructure.api.hardware.M5ExtraLargeEphemeral
import com.atlassian.performance.tools.awsinfrastructure.api.hardware.Volume
import com.atlassian.performance.tools.infrastructure.api.database.MySqlDatabase
import com.atlassian.performance.tools.infrastructure.api.dataset.Dataset
import com.atlassian.performance.tools.infrastructure.api.dataset.HttpDatasetPackage
import com.atlassian.performance.tools.infrastructure.api.jira.JiraHomePackage
import com.atlassian.performance.tools.infrastructure.api.jira.flow.JiraNodeFlow
import org.junit.Test
import java.net.URI
import java.time.Duration
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class FlowServerFormulaIT {

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
        val mysqlHook = AsyncTcpServerHook(
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
        val serverFormula = FlowServerFormula.Builder()
            .node(
                JiraNodeProvisioning.Builder(dataset.jiraHomeSource)
                    .flow(
                        JiraNodeFlow().apply { hookPreInstall(mysqlHook) }
                    )
                    .build()
            )
            .network(network)
            .build()

        val provisionedJira = serverFormula.provision(
            investment = investment,
            pluginsTransport = IntegrationTestRuntime.aws.jiraStorage(nonce),
            resultsTransport = IntegrationTestRuntime.aws.resultsStorage(nonce),
            key = CompletableFuture.completedFuture(sshKey),
            roleProfile = IntegrationTestRuntime.aws.shortTermStorageAccess(),
            aws = IntegrationTestRuntime.aws
        )

        provisionedJira.resource.release().get(1, TimeUnit.MINUTES)
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
            useCase = "Test Server provisioning flow API",
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