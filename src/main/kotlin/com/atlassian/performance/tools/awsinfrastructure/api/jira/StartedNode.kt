package com.atlassian.performance.tools.awsinfrastructure.api.jira

import com.atlassian.performance.tools.aws.api.Storage
import com.atlassian.performance.tools.awsinfrastructure.api.aws.AwsCli
import com.atlassian.performance.tools.infrastructure.api.jira.JiraGcLog
import com.atlassian.performance.tools.infrastructure.api.process.RemoteMonitoringProcess
import com.atlassian.performance.tools.ssh.api.Ssh
import org.apache.logging.log4j.LogManager
import java.time.Duration

class StartedNode(
    private val name: String,
    private val jiraHome: String,
    private val analyticLogs: String,
    private val resultsTransport: Storage,
    private val jiraPath: String,
    private val monitoringProcesses: List<RemoteMonitoringProcess>,
    private val ssh: Ssh
) {
    private val logger = LogManager.getLogger(this::class.java)
    private val resultsDirectory = "results"

    fun gatherResults() {
        ssh.newConnection().use { shell ->
            monitoringProcesses.forEach {
                try {
                    it.stop(shell)
                } catch (e: Exception) {
                    logger.error("Failed to stop $it process", e)
                }
            }
            val nodeResultsDirectory = "$resultsDirectory/'$name'"
            val threadDumpsFolder = "thread-dumps"
            listOf(
                "mkdir -p $nodeResultsDirectory",
                "cp $jiraPath/logs/catalina.out $nodeResultsDirectory",
                "cp $jiraPath/logs/*access* $nodeResultsDirectory",
                "mkdir -p $nodeResultsDirectory/$threadDumpsFolder",
                "cp $threadDumpsFolder/* $nodeResultsDirectory/$threadDumpsFolder",
                "cp $jiraHome/log/atlassian-jira.log* $nodeResultsDirectory",
                "cp ${JiraGcLog(jiraPath).path()} $nodeResultsDirectory",
                "cp /var/log/syslog $nodeResultsDirectory",
                "cp /var/log/cloud-init.log $nodeResultsDirectory",
                "cp /var/log/cloud-init-output.log $nodeResultsDirectory"
            )
                .plus(monitoringProcesses.map { "cp ${it.getResultPath()} $nodeResultsDirectory" })
                .plus("find $nodeResultsDirectory -empty -type f -delete")
                .forEach { shell.safeExecute(it) }
            AwsCli().upload(
                location = resultsTransport.location,
                ssh = shell,
                source = resultsDirectory,
                timeout = Duration.ofMinutes(10)
            )
        }
    }

    fun gatherAnalyticLogs() {
        ssh.newConnection().use {
            it.execute("cp -r $analyticLogs/analytics-logs $resultsDirectory")
            it.execute("find $resultsDirectory/analytics-logs/ -maxdepth 1 -type f -name '*.gz' -exec gunzip {} +")
            AwsCli().upload(
                location = resultsTransport.location,
                ssh = it,
                source = resultsDirectory,
                timeout = Duration.ofMinutes(2)
            )
        }
    }

    internal fun copy(name: String, analyticLogs: String): StartedNode {
        return StartedNode(
            name = name,
            jiraHome = this.jiraHome,
            analyticLogs = analyticLogs,
            resultsTransport = this.resultsTransport,
            jiraPath = this.jiraPath,
            monitoringProcesses = this.monitoringProcesses,
            ssh = this.ssh
        )
    }

    override fun toString() = name

    fun toStoppableNode(): StoppableNode = StoppableNode.Builder(jiraPath, ssh).build()
}
