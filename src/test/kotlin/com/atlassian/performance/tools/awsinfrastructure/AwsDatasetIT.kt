package com.atlassian.performance.tools.awsinfrastructure

import com.atlassian.performance.tools.awsinfrastructure.IntegrationTestRuntime.aws
import com.atlassian.performance.tools.awsinfrastructure.IntegrationTestRuntime.taskWorkspace
import com.atlassian.performance.tools.awsinfrastructure.api.DatasetCatalogue
import com.atlassian.performance.tools.ssh.api.Ssh
import org.junit.Test

class AwsDatasetIT {

    private val workspace = taskWorkspace.isolateTest(javaClass.simpleName)
    private val sourceDataset = DatasetCatalogue().largeJira()

    @Test
    fun shouldRemoveBackups() {
        AwsDataset(sourceDataset)
            .modify(
                aws = aws,
                workspace = workspace,
                newDatasetName = "large-jira-without-backups"
            ) { infrastructure ->
                val jiraHome = infrastructure.jira.jiraHome
                val backupPath = "${jiraHome.location}/export"
                Ssh(jiraHome.host)
                    .newConnection()
                    .use { ssh ->
                        val listCommand = "ls -lh $backupPath"
                        val listOutput = ssh.execute(listCommand).output
                        println("$ $listCommand\n$listOutput")
                        ssh.execute("rm -r $backupPath")
                    }
            }
    }
}
