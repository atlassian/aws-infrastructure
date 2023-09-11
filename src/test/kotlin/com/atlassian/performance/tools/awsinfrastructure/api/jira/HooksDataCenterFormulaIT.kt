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
import java.nio.file.Path
import java.time.Duration
import java.util.stream.Collectors
import kotlin.streams.toList

class HooksDataCenterFormulaIT {
    private val jiraVersion = "9.4.9"
    private val workspace = IntegrationTestRuntime.taskWorkspace.isolateTest(javaClass.simpleName).directory
    // it needs to be instantiated after IntegrationTestRuntime constructor
    private val logger: Logger = LogManager.getLogger(this::class.java)
    private val s3Bucket = URI("https://s3-eu-central-1.amazonaws.com/")
        .resolve("jpt-custom-datasets-storage-a008820-datasetbucket-1nrja8d1upind/")
        .resolve("dataset-a533e558-e5c5-46e7-9398-5aeda84d793a/")
    private val mysql = HttpDatasetPackage(
        uri = s3Bucket.resolve("database.tar.bz2"),
        downloadTimeout = Duration.ofMinutes(6)
    )
    private val jiraHome = JiraHomePackage(
        HttpDatasetPackage(
            uri = s3Bucket.resolve("jirahome.tar.bz2"),
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

    private fun makeFailureObservable(block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            val potentialJiraProblems = findPotentialJiraProblems()
            logger.error("Failed to provision DC. Report downloaded to $workspace")
            e.addSuppressed(RuntimeException("Potential Jira problems found in workspace: $potentialJiraProblems"))
            throw e
        }
    }

    @Test
    fun shouldProvisionDc() {
        // given
        val database = DockerMysqlServer.Builder(infra.databaseServerRoom, mysql)
            .source(
                HttpDatasetPackage(
                    uri = s3Bucket.resolve("database.tar.bz2"),
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
        val loadBalancer = infra.balance(ApacheEc2LoadBalancerFormula())
        val dcPlan = JiraDataCenterPlan.Builder(nodePlans, loadBalancer)
            .instanceHooks(
                PreInstanceHooks.default()
                    .also { it.insert(database) }
                    .also { it.insert(NfsSharedHome(jiraHome, infra.sharedHomeServerRoom, infra)) }
            )
            .build()

        // when
        var dataCenter: JiraInstance? = null
        makeFailureObservable {
            dataCenter = dcPlan.materialize()
        }
        // then
        makeFailureObservable {
            dataCenter!!.nodes.forEach { node ->
                val installed = node.installed
                val serverXml = installed
                    .installation
                    .resolve("conf/server.xml")
                    .download(Files.createTempFile("downloaded-config", ".xml"))
                assertThat(serverXml.readText()).contains("<Connector port=\"${installed.http.tcp.port}\"")
                assertThat(node.pid).isPositive()
                installed.http.tcp.ssh.newConnection().use { ssh ->
                    ssh.execute("wget ${dataCenter!!.address}")
                }
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
            val jiraResponse = dataCenter!!.address.addressPublicly().toURL().readText()
            assertThat(jiraResponse).contains("<body>")
        }.assertAll()
    }

    private fun findPotentialJiraProblems(): Map<Path, List<String>> {
        val jiraLogFileName = "atlassian-jira.log"
        val keywords = setOf("fatal", "error", "exception", "timeout", "waiting", "fail", "unable", "lock", "block")

        val result = mutableMapOf<Path, MutableList<String>>()
        val files = findFiles(workspace, jiraLogFileName)

        for (file in files) {
            val filteredLines = filterFile(file, keywords)
            result.putIfAbsent(file, mutableListOf())
            result[file]!!.addAll(filteredLines)
        }
        return result
    }

    private fun findFiles(start: Path, fileName: String): List<Path> {
        return Files.walk(start)
            .filter { path -> Files.isRegularFile(path) && path.fileName.toString() == fileName }
            .collect(Collectors.toList())
    }

    private fun filterFile(file: Path, keywords: Set<String>): List<String> {
        return file.toFile().bufferedReader().lines().filter { line ->
            keywords.any { keyword -> line.contains(keyword, ignoreCase = true) }
        }.toList()
    }

}
