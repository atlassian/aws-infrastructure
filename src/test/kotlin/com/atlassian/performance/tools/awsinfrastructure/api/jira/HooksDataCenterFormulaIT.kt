package com.atlassian.performance.tools.awsinfrastructure.api.jira

import com.atlassian.performance.tools.aws.api.Investment
import com.atlassian.performance.tools.aws.api.SshKeyFormula
import com.atlassian.performance.tools.awsinfrastructure.IntegrationTestRuntime
import com.atlassian.performance.tools.awsinfrastructure.NetworkFormula
import com.atlassian.performance.tools.awsinfrastructure.api.hardware.M5ExtraLargeEphemeral
import com.atlassian.performance.tools.awsinfrastructure.api.hardware.Volume
import com.atlassian.performance.tools.infrastructure.api.database.DockerMysqlServer
import com.atlassian.performance.tools.infrastructure.api.dataset.HttpDatasetPackage
import com.atlassian.performance.tools.infrastructure.api.distribution.PublicJiraSoftwareDistribution
import com.atlassian.performance.tools.infrastructure.api.jira.JiraHomePackage
import com.atlassian.performance.tools.infrastructure.api.jira.JiraLaunchTimeouts
import com.atlassian.performance.tools.infrastructure.api.jira.install.ParallelInstallation
import com.atlassian.performance.tools.infrastructure.api.jira.install.hook.PreInstallHooks
import com.atlassian.performance.tools.infrastructure.api.jira.instance.JiraDataCenterPlan
import com.atlassian.performance.tools.infrastructure.api.jira.instance.JiraNodePlan
import com.atlassian.performance.tools.infrastructure.api.jira.instance.PreInstanceHooks
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
import java.util.concurrent.CompletableFuture

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
        val stack: JiraStack = provisionDependencies()
        val database = DockerMysqlServer.Builder(stack.forDatabase(), mysql)
            .source(
                HttpDatasetPackage(
                    uri = datasetUri.resolve("database.tar.bz2"),
                    downloadTimeout = Duration.ofMinutes(6)
                )
            )
            .build()
        val upgrade = RestUpgrade(JiraLaunchTimeouts.Builder().build(), "admin", "admin")
        val installation = ParallelInstallation(jiraHome, PublicJiraSoftwareDistribution("8.13.0"), AdoptOpenJDK())
        val dcPlan = JiraDataCenterPlan.Builder(stack.forJiraNodes())
            .nodePlans(
                (1..2).map {
                    JiraNodePlan.Builder(stack.forJiraNodes())
                        .installation(installation)
                        .start(JiraLaunchScript())
                        .hooks(PreInstallHooks.default().also { it.postStart.insert(upgrade) })
                        .build()
                }
            )
            .instanceHooks(
                PreInstanceHooks.default()
                    .also { it.insert(database) }
                    .also { it.insert(NfsSharedHome(jiraHome, stack.forSharedHome(), stack)) }
            )
            .balancerPlan(ApacheProxyPlan(stack.forLoadBalancer()))
            .build()

        // when
        val dataCenter = dcPlan.materialize()

        // then
        dataCenter.nodes.forEach { node ->
            val installed = node.installed
            val serverXml = installed
                .installation
                .resolve("conf/server.xml")
                .download(Files.createTempFile("downloaded-config", ".xml"))
            assertThat(serverXml.readText()).contains("<Connector port=\"${installed.http.tcp.port}\"")
            assertThat(node.pid).isPositive()
            installed.http.tcp.ssh.newConnection().use { ssh ->
                ssh.execute("wget ${dataCenter.address}")
            }
        }
        val reports = dcPlan.report().downloadTo(Files.createTempDirectory("jira-dc-plan-"))
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

    private fun provisionDependencies(): JiraStack {
        val aws = IntegrationTestRuntime.aws
        val nonce = UUID.randomUUID().toString()
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
        return JiraStack.Builder(
            aws,
            network,
            investment,
            CompletableFuture.completedFuture(sshKey),
            aws.shortTermStorageAccess()
        )
            .databaseComputer(M5ExtraLargeEphemeral())
            .databaseVolume(Volume(100))
            .build()
    }
}
