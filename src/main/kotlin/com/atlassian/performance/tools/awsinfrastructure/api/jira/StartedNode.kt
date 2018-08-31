package com.atlassian.performance.tools.awsinfrastructure.api.jira

import com.atlassian.performance.tools.aws.api.Storage
import com.atlassian.performance.tools.awsinfrastructure.AwsCli
import com.atlassian.performance.tools.infrastructure.api.jira.JiraGcLog
import com.atlassian.performance.tools.infrastructure.api.os.MonitoringProcess
import com.atlassian.performance.tools.ssh.api.Ssh
import java.time.Duration

data class StartedNode(
    private val name: String,
    private val jiraHome: String,
    private val analyticLogs: String,
    private val resultsTransport: Storage,
    private val unpackedProduct: String,
    private val monitoringProcesses: List<MonitoringProcess>,
    private val ssh: Ssh
) {
    private val resultsDirectory = "results"

    fun gatherResults() {
        ssh.newConnection().use { shell ->
            monitoringProcesses.forEach { shell.stopProcess(it.process) }
            val nodeResultsDirectory = "$resultsDirectory/'$name'"
            listOf(
                "mkdir -p $nodeResultsDirectory",
                "cp $unpackedProduct/logs/catalina.out $nodeResultsDirectory",
                "cp $unpackedProduct/logs/*access* $nodeResultsDirectory",
                "cp $jiraHome/log/atlassian-jira.log $nodeResultsDirectory",
                "cp ${JiraGcLog(unpackedProduct).path()} $nodeResultsDirectory",
                "cp /var/log/syslog $nodeResultsDirectory",
                "cp /var/log/cloud-init.log $nodeResultsDirectory",
                "cp /var/log/cloud-init-output.log $nodeResultsDirectory"
            )
                .plus(monitoringProcesses.map { "cp ${it.logFile} $nodeResultsDirectory" })
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

    override fun toString() = name
}