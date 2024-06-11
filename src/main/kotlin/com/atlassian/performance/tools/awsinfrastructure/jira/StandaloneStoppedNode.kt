package com.atlassian.performance.tools.awsinfrastructure.jira

import com.atlassian.performance.tools.aws.api.Storage
import com.atlassian.performance.tools.awsinfrastructure.api.jira.StartedNode
import com.atlassian.performance.tools.infrastructure.api.jira.JiraLaunchTimeouts
import com.atlassian.performance.tools.infrastructure.api.jvm.JavaDevelopmentKit
import com.atlassian.performance.tools.infrastructure.api.jvm.ThreadDump
import com.atlassian.performance.tools.infrastructure.api.os.OsMetric
import com.atlassian.performance.tools.infrastructure.api.process.RemoteMonitoringProcess
import com.atlassian.performance.tools.infrastructure.api.profiler.Profiler
import com.atlassian.performance.tools.ssh.api.Ssh
import com.atlassian.performance.tools.ssh.api.SshConnection
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.io.StringReader
import java.net.URI
import java.time.Duration
import java.time.Duration.ofMinutes
import java.time.Duration.ofSeconds
import java.time.Instant.now
import javax.json.Json

internal class StandaloneStoppedNode(
    private val name: String,
    override val jiraHome: String,
    private val analyticLogs: String,
    private val resultsTransport: Storage,
    private val unpackedProduct: String,
    private val osMetrics: List<OsMetric>,
    private val waitForRunning: Boolean,
    private val waitForUpgrades: Boolean,
    private val launchTimeouts: JiraLaunchTimeouts,
    private val jdk: JavaDevelopmentKit,
    private val profiler: Profiler,
    override val ssh: Ssh,
    private val adminPasswordPlainText: String
) : StoppedNode {
    private val logger: Logger = LogManager.getLogger(this::class.java)

    override fun start(updateConfigurationFunction: List<(ssh: Ssh, unpackedProduct: String) -> Unit>): StartedNode {
        logger.info("Starting '$name'...")
        val monitoringProcesses = mutableListOf<RemoteMonitoringProcess>()

        ssh.newConnection().use { sshConnection ->
            osMetrics.forEach { metric ->
                monitoringProcesses.add(metric.start(sshConnection))
            }

            updateConfigurationFunction.forEach {
                it(ssh, unpackedProduct)
            }

            startJira(sshConnection)
            val pid = pid(sshConnection)
            monitoringProcesses.add(jdk.jstatMonitoring.start(sshConnection, pid))
            profiler.start(sshConnection, pid)?.let { monitoringProcesses.add(it) }
            val threadDump = ThreadDump(pid, jdk)
            try {
                waitForRunning(sshConnection, threadDump)
                waitForUpgrades(sshConnection, threadDump)
            } catch (exception: Exception) {
                StartedNode(
                    name = name,
                    jiraHome = jiraHome,
                    analyticLogs = analyticLogs,
                    resultsTransport = resultsTransport,
                    jiraPath = unpackedProduct,
                    monitoringProcesses = monitoringProcesses,
                    ssh = ssh
                ).gatherResults()
                throw Exception("Failed to start the Jira node.", exception)
            }
        }

        logger.info("'$name' is started")

        return StartedNode(
            name = name,
            jiraHome = jiraHome,
            analyticLogs = analyticLogs,
            resultsTransport = resultsTransport,
            jiraPath = unpackedProduct,
            monitoringProcesses = monitoringProcesses,
            ssh = ssh
        )
    }

    private fun startJira(
        ssh: SshConnection
    ) {
        ssh.execute(
            """
            |${jdk.use()}
            |./$unpackedProduct/bin/start-jira.sh
            """.trimMargin(),
            ofMinutes(1)
        )
    }

    private fun pid(
        ssh: SshConnection
    ): Int {
        return ssh.execute("cat $unpackedProduct/work/catalina.pid").output.trim().toInt()
    }

    private fun waitForRunning(
        ssh: SshConnection,
        threadDump: ThreadDump
    ) {
        if (!waitForRunning) {
            logger.debug("Skipping Jira startup wait")
            return
        }
        val statusEndpoint = URI("http://admin:$adminPasswordPlainText@localhost:8080/status")
        pollJira(
            launchTimeouts.initTimeout,
            ssh,
            threadDump,
            "RUNNING state"
        ) {
            val response = ssh.safeExecute("curl $statusEndpoint", launchTimeouts.unresponsivenessTimeout).output
            Json.createParser(StringReader(response)).use { jsonParser ->
                if (jsonParser.hasNext()) {
                    jsonParser.`object`.getString("state") == "RUNNING"
                } else {
                    false
                }
            }
        }
    }

    /**
     * Possible side effect: when wrong password provided increases cwd_user.invalidPasswordAttempts and that might result in
     * - captcha input being needed at login
     * - virtual user failing to login because of captcha
     */
    private fun waitForUpgrades(
        ssh: SshConnection,
        threadDump: ThreadDump
    ) {
        if (!waitForUpgrades) {
            logger.debug("Skipping Jira upgrade wait")
            return
        }
        logger.debug("Waiting for Jira node to upgrade...")
        val upgradesEndpoint = URI("http://admin:$adminPasswordPlainText@localhost:8080/rest/api/2/upgrade")
        waitForStatusToChange(
            statusQuo = "000",
            timeout = launchTimeouts.offlineTimeout,
            ssh = ssh,
            uri = upgradesEndpoint,
            threadDump = threadDump
        )
        waitForStatusToChange(
            statusQuo = "503",
            timeout = launchTimeouts.initTimeout,
            ssh = ssh,
            uri = upgradesEndpoint,
            threadDump = threadDump
        )
        ssh.execute(
            cmd = "curl --silent --retry 6 -X POST $upgradesEndpoint",
            timeout = ofSeconds(15)
        )
        waitForStatusToChange(
            statusQuo = "303",
            timeout = launchTimeouts.upgradeTimeout,
            ssh = ssh,
            uri = upgradesEndpoint,
            threadDump = threadDump
        )
    }

    private fun waitForStatusToChange(
        statusQuo: String,
        uri: URI,
        timeout: Duration,
        ssh: SshConnection,
        threadDump: ThreadDump
    ) {
        pollJira(
            timeout,
            ssh,
            threadDump,
            "upgrades passing $statusQuo status"
        ) {
            val newStatus = ssh.safeExecute(
                cmd = "curl --silent --write-out '%{http_code}' --output /dev/null -X GET $uri",
                timeout = launchTimeouts.unresponsivenessTimeout
            ).output
            newStatus != statusQuo
        }
    }

    private fun pollJira(
        timeout: Duration,
        ssh: SshConnection,
        threadDump: ThreadDump,
        checkName: String,
        check: () -> Boolean
    ) {
        val backoff = ofSeconds(10)
        val deadline = now() + timeout
        while (true) {
            if (check()) {
                break
            }
            if (deadline < now()) {
                if (logger.isDebugEnabled) {
                    val logContent = ssh.safeExecute(
                        cmd = "cat $jiraHome/log/atlassian-jira.log",
                        timeout = ofMinutes(3)
                    ).output
                    logger.debug("Jira log file: <<$logContent>>")
                }
                throw Exception("Jira didn't pass $checkName check in $timeout")
            }
            threadDump.gather(ssh, "thread-dumps")
            Thread.sleep(backoff.toMillis())
        }
    }


    override fun toString(): String {
        return "StandaloneStoppedNode(name='$name', jiraHome='$jiraHome', analyticLogs='$analyticLogs', resultsTransport=$resultsTransport, unpackedProduct='$unpackedProduct', osMetrics=$osMetrics, waitForRunning=$waitForRunning, waitForUpgrades=$waitForUpgrades, launchTimeouts=$launchTimeouts, jdk=$jdk, profiler=$profiler)"
    }

}
