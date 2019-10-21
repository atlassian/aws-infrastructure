package com.atlassian.performance.tools.awsinfrastructure.api.jira

import com.atlassian.performance.tools.aws.api.Aws
import com.atlassian.performance.tools.aws.api.Investment
import com.atlassian.performance.tools.aws.api.SshKey
import com.atlassian.performance.tools.aws.api.SshKeyFormula
import com.atlassian.performance.tools.awsinfrastructure.IntegrationTestRuntime
import com.atlassian.performance.tools.awsinfrastructure.NetworkFormula
import com.atlassian.performance.tools.awsinfrastructure.api.Network
import com.atlassian.performance.tools.awsinfrastructure.api.hardware.M5ExtraLargeEphemeral
import com.atlassian.performance.tools.awsinfrastructure.api.hardware.Volume
import com.atlassian.performance.tools.awsinfrastructure.host.TcpHostFormula
import com.atlassian.performance.tools.infrastructure.api.database.DockerMysqlServer
import com.atlassian.performance.tools.infrastructure.api.dataset.HttpDatasetPackage
import com.atlassian.performance.tools.infrastructure.api.jira.JiraHomePackage
import com.atlassian.performance.tools.infrastructure.api.jira.JiraLaunchTimeouts
import com.atlassian.performance.tools.infrastructure.api.jira.hook.JiraNodeHooks
import com.atlassian.performance.tools.infrastructure.api.jira.hook.instance.JiraInstanceHooks
import com.atlassian.performance.tools.infrastructure.api.jira.hook.start.RestUpgrade
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.net.URI
import java.nio.file.Files
import java.time.Duration
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class HooksDataCenterFormulaIT {

    private val workspace = IntegrationTestRuntime.taskWorkspace.isolateTest(javaClass.simpleName)
    private val datasetUri = URI("https://s3-eu-west-1.amazonaws.com/")
        .resolve("jpt-custom-datasets-storage-a008820-datasetbucket-1sjxdtrv5hdhj/")
        .resolve("dataset-f8dba866-9d1b-492e-b76c-f4a78ac3958c/")
    private val jiraHome = JiraHomePackage(
        HttpDatasetPackage(
            uri = datasetUri.resolve("jirahome.tar.bz2"),
            downloadTimeout = Duration.ofMinutes(6)
        )
    )
    private val lifespan = Duration.ofMinutes(30)

    @Test
    fun shouldProvisionDc() {
        val aws = IntegrationTestRuntime.aws
        val nonce = UUID.randomUUID().toString()
        val (investment, sshKey, network) = provisionDependencies(aws, nonce)
        val mysql = prepareMysql(aws, sshKey, network, investment)
        val dcFormula = HooksDataCenterFormula.Builder(jiraHome)
            .instance(JiraInstanceHooks().also { it.hook(mysql) })
            .nodes(
                (1..2).map {
                    JiraNodeProvisioning.Builder(jiraHome)
                        .hooks(
                            JiraNodeHooks.default()
                                .also { it.hook(RestUpgrade(JiraLaunchTimeouts.Builder().build(), "admin", "admin")) }
                        )
                        .build()
                }
            )
            .network(network)
            .build()
        val resultsTransport = aws.resultsStorage(nonce)

        val provisionedJira = dcFormula.provision(
            investment = investment,
            pluginsTransport = aws.jiraStorage(nonce),
            resultsTransport = resultsTransport,
            key = CompletableFuture.completedFuture(sshKey),
            roleProfile = aws.shortTermStorageAccess(),
            aws = aws
        )
        provisionedJira.jira.gatherResults()

        val results = resultsTransport
            .download(Files.createTempDirectory("hooks-dc-formula"))
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
    ): AwsDcDependencies {
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
        return AwsDcDependencies(investment, sshKey, network)
    }

    private fun prepareMysql(
        aws: Aws,
        sshKey: SshKey,
        network: Network,
        investment: Investment
    ): DockerMysqlServer = DockerMysqlServer.Builder(
        hostSupplier = TcpHostFormula.Builder(
            aws,
            sshKey,
            network
        )
            .port(3306)
            .name("MySQL server")
            .investment(investment)
            .computer(M5ExtraLargeEphemeral())
            .volume(Volume(100))
            .stackTimeout(Duration.ofMinutes(4))
            .build(),
        source = HttpDatasetPackage(
            uri = datasetUri.resolve("database.tar.bz2"),
            downloadTimeout = Duration.ofMinutes(6)
        )
    ).build()

    private data class AwsDcDependencies(
        val investment: Investment,
        val sshKey: SshKey,
        val network: Network
    )
}
