package com.atlassian.performance.tools.awsinfrastructure.api.jira

import com.atlassian.performance.tools.aws.api.Aws
import com.atlassian.performance.tools.aws.api.Investment
import com.atlassian.performance.tools.aws.api.SshKey
import com.atlassian.performance.tools.aws.api.SshKeyFormula
import com.atlassian.performance.tools.awsinfrastructure.IntegrationTestRuntime
import com.atlassian.performance.tools.awsinfrastructure.NetworkFormula
import com.atlassian.performance.tools.awsinfrastructure.api.DatasetCatalogue
import com.atlassian.performance.tools.awsinfrastructure.api.Network
import com.atlassian.performance.tools.awsinfrastructure.api.hardware.M5ExtraLargeEphemeral
import com.atlassian.performance.tools.awsinfrastructure.api.hardware.Volume
import com.atlassian.performance.tools.awsinfrastructure.host.TcpHostFormula
import com.atlassian.performance.tools.infrastructure.api.database.DockerMysqlServer
import com.atlassian.performance.tools.infrastructure.api.dataset.HttpDatasetPackage
import com.atlassian.performance.tools.infrastructure.api.distribution.PublicJiraSoftwareDistribution
import com.atlassian.performance.tools.infrastructure.api.jira.JiraHomePackage
import com.atlassian.performance.tools.infrastructure.api.jira.JiraLaunchTimeouts
import com.atlassian.performance.tools.infrastructure.api.jira.install.ParallelInstallation
import com.atlassian.performance.tools.infrastructure.api.jira.install.hook.PreInstallHooks
import com.atlassian.performance.tools.infrastructure.api.jira.instance.JiraDataCenterPlan
import com.atlassian.performance.tools.infrastructure.api.jira.instance.PreInstanceHooks
import com.atlassian.performance.tools.infrastructure.api.jira.node.JiraNodePlan
import com.atlassian.performance.tools.infrastructure.api.jira.sharedhome.NfsSharedHome
import com.atlassian.performance.tools.infrastructure.api.jira.start.JiraLaunchScript
import com.atlassian.performance.tools.infrastructure.api.jira.start.hook.RestUpgrade
import com.atlassian.performance.tools.infrastructure.api.jvm.AdoptOpenJDK
import com.atlassian.performance.tools.infrastructure.api.loadbalancer.ApacheProxyPlan
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.net.URI
import java.nio.file.Files
import java.time.Duration
import java.util.*

class HooksDataCenterFormulaIT {

    private val workspace = IntegrationTestRuntime.taskWorkspace.isolateTest(javaClass.simpleName)
    private val datasetUri = URI("https://s3-eu-west-1.amazonaws.com/")
        .resolve("jpt-custom-datasets-storage-a008820-datasetbucket-1sjxdtrv5hdhj/")
        .resolve("dataset-f8dba866-9d1b-492e-b76c-f4a78ac3958c/")
    private val mysql = HttpDatasetPackage(
        uri = datasetUri.resolve("database.tar.bz2"),
        downloadTimeout = Duration.ofMinutes(6)
    )
    private val jiraHome = JiraHomePackage(
        HttpDatasetPackage(
            uri = datasetUri.resolve("jirahome.tar.bz2"),
            downloadTimeout = Duration.ofMinutes(6)
        )
    )
    private val lifespan = Duration.ofMinutes(30)

    @Test
    fun shouldProvisionDc() {
        // given
        val aws = IntegrationTestRuntime.aws
        val nonce = UUID.randomUUID().toString()
        val infrastructure: JiraInfrastructure = provisionDependencies(aws, nonce)
        val database = DockerMysqlServer.Builder(infrastructure.forMysql(), mysql)
            .source(
                HttpDatasetPackage(
                    uri = datasetUri.resolve("database.tar.bz2"),
                    downloadTimeout = Duration.ofMinutes(6)
                )
            )
            .build()
        val upgrade = RestUpgrade(JiraLaunchTimeouts.Builder().build(), "admin", "admin")
        val installation = ParallelInstallation(jiraHome, PublicJiraSoftwareDistribution("8.13.0"), AdoptOpenJDK())
        val dcPlan = JiraDataCenterPlan.Builder(infrastructure.forJiraNodes())
            .nodePlans(
                (1..2).map {
                    JiraNodePlan.Builder()
                        .installation(installation)
                        .start(JiraLaunchScript())
                        .hooks(PreInstallHooks.default().also { it.postStart.insert(upgrade) })
                        .build()
                }
            )
            .instanceHooks(
                PreInstanceHooks.default()
                    .also { it.insert(database) }
                    .also { it.insert(NfsSharedHome(jiraHome, infrastructure.forSharedHome())) }
            )
            .balancerPlan(ApacheProxyPlan(infrastructure.forLoadBalancer()))
            .build()

        // when
        dcPlan.materialize()
        val reports = dcPlan.report().downloadTo(Files.createTempDirectory("jira-dc-plan-"))

        // then
        assertThat(reports).isDirectory()
        val fileTree = reports
            .walkTopDown()
            .map { reports.toPath().relativize(it.toPath()) }
            .toList()
        assertThat(fileTree.map { it.toString() }).contains(
            "jira-node-1/root/atlassian-jira-software-7.13.0-standalone/logs/catalina.out",
            "jira-node-1/root/~/jpt-jstat.log",
            "jira-node-2/root/atlassian-jira-software-7.13.0-standalone/logs/catalina.out"
        )
        assertThat(fileTree.filter { it.fileName.toString() == "atlassian-jira.log" })
            .`as`("Jira log from $fileTree")
            .isNotEmpty
        assertThat(fileTree.filter { it.fileName.toString().startsWith("atlassian-jira-gc") })
            .`as`("GC logs from $fileTree")
            .isNotEmpty
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
}
