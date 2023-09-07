package com.atlassian.performance.tools.awsinfrastructure.api.jira

import com.atlassian.performance.tools.aws.api.Investment
import com.atlassian.performance.tools.awsinfrastructure.IntegrationTestRuntime
import com.atlassian.performance.tools.awsinfrastructure.api.hardware.M5ExtraLargeEphemeral
import com.atlassian.performance.tools.awsinfrastructure.api.hardware.Volume
import com.atlassian.performance.tools.awsinfrastructure.api.loadbalancer.ApacheEc2LoadBalancerFormula
import com.atlassian.performance.tools.infrastructure.api.database.DockerMysqlServer
import com.atlassian.performance.tools.infrastructure.api.dataset.HttpDatasetPackage
import com.atlassian.performance.tools.infrastructure.api.distribution.PublicJiraSoftwareDistribution
import com.atlassian.performance.tools.infrastructure.api.jira.JiraHomePackage
import com.atlassian.performance.tools.infrastructure.api.jira.JiraLaunchTimeouts
import com.atlassian.performance.tools.infrastructure.api.jira.install.ParallelInstallation
import com.atlassian.performance.tools.infrastructure.api.jira.install.hook.PreInstallHooks
import com.atlassian.performance.tools.infrastructure.api.jira.instance.JiraDataCenterPlan
import com.atlassian.performance.tools.infrastructure.api.jira.instance.JiraInstance
import com.atlassian.performance.tools.infrastructure.api.jira.instance.JiraNodePlan
import com.atlassian.performance.tools.infrastructure.api.jira.instance.PreInstanceHooks
import com.atlassian.performance.tools.infrastructure.api.jira.report.Reports
import com.atlassian.performance.tools.infrastructure.api.jira.sharedhome.NfsSharedHome
import com.atlassian.performance.tools.infrastructure.api.jira.start.JiraLaunchScript
import com.atlassian.performance.tools.infrastructure.api.jira.start.StartedJira
import com.atlassian.performance.tools.infrastructure.api.jira.start.hook.PostStartHook
import com.atlassian.performance.tools.infrastructure.api.jira.start.hook.PostStartHooks
import com.atlassian.performance.tools.infrastructure.api.jira.start.hook.RestUpgrade
import com.atlassian.performance.tools.infrastructure.api.jvm.AdoptOpenJDK
import com.atlassian.performance.tools.ssh.api.SshConnection
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions
import org.junit.Test
import java.net.URI
import java.nio.file.Files
import java.time.Duration

class HooksDataCenterFormulaIT {
    private val logger: Logger = LogManager.getLogger(this::class.java)
    private val jiraVersion = "8.13.0"
    private val workspace = IntegrationTestRuntime.taskWorkspace.isolateTest(javaClass.simpleName).directory
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
    private val infra: LegacyAwsInfrastructure = LegacyAwsInfrastructure.Builder(
        IntegrationTestRuntime.aws,
        Investment("Test Server provisioning hook API", Duration.ofMinutes(30))
    )
        .databaseComputer(M5ExtraLargeEphemeral())
        .databaseVolume(Volume(100))
        .workspace(workspace)
        .build()

    @Test
    fun shouldProvisionDc() {
        // given
        val database = DockerMysqlServer.Builder(infra.databaseServerRoom, mysql)
            .source(
                HttpDatasetPackage(
                    uri = datasetUri.resolve("database.tar.bz2"),
                    downloadTimeout = Duration.ofMinutes(6)
                )
            )
            .setPassword("admin", "admin")
            .resetCaptcha("admin")
            .build()
        val startingNodeLogHook = object : PostStartHook {
            override fun call(ssh: SshConnection, jira: StartedJira, hooks: PostStartHooks, reports: Reports) {
                logger.info("Jira node is starting at ${jira.installed.http.addressPublicly()}")
            }
        }
        val upgrade = RestUpgrade(JiraLaunchTimeouts.Builder().build(), "admin", "admin")
        val installation = ParallelInstallation(jiraHome, PublicJiraSoftwareDistribution(jiraVersion), AdoptOpenJDK())
        val nodePlans = (1..2).map {
            JiraNodePlan.Builder(infra.jiraNodesServerRoom)
                .installation(installation)
                .start(JiraLaunchScript())
                .hooks(PreInstallHooks.default().also {
                    it.postStart.insert(startingNodeLogHook)
                    it.postStart.insert(upgrade)
                })
                .build()
        }
        val dcPlan = JiraDataCenterPlan.Builder(nodePlans, infra.balance(ApacheEc2LoadBalancerFormula()))
            .instanceHooks(
                PreInstanceHooks.default()
                    .also { it.insert(database) }
                    .also { it.insert(NfsSharedHome(jiraHome, infra.sharedHomeServerRoom, infra)) }
            )
            .build()

        // when
        val dataCenter: JiraInstance
        try {
            dataCenter = dcPlan.materialize()
        } catch (e: Exception) {
            dcPlan.report().downloadTo(workspace)
            logger.error("Failed to provision DC. Report downloaded to $workspace")
            throw e
        }

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
        val reports = dcPlan.report().downloadTo(workspace)
        assertThat(reports).isDirectory()
        val fileTree = reports
            .walkTopDown()
            .map { reports.toPath().relativize(it.toPath()) }
            .toList()
        SoftAssertions().apply {
            assertThat(fileTree.map { it.toString() }).contains(
                "jira-node-1/atlassian-jira-software-$jiraVersion-standalone/logs/catalina.out",
                "jira-node-1/~/jpt-jstat.log",
                "jira-node-2/atlassian-jira-software-$jiraVersion-standalone/logs/catalina.out"
            )
            assertThat(fileTree.filter { it.fileName.toString() == "atlassian-jira.log" })
                .`as`("Jira log from $fileTree")
                .isNotEmpty
            assertThat(fileTree.filter { it.fileName.toString().startsWith("atlassian-jira-gc") })
                .`as`("GC logs from $fileTree")
                .isNotEmpty
        }.assertAll()
    }
}
