package com.atlassian.performance.tools.awsinfrastructure

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain
import com.amazonaws.regions.Regions
import com.atlassian.performance.tools.aws.api.Aws
import com.atlassian.performance.tools.awsinfrastructure.api.DatasetCatalogue
import com.atlassian.performance.tools.ssh.api.Ssh
import com.atlassian.performance.tools.workspace.api.RootWorkspace
import org.apache.logging.log4j.core.config.ConfigurationFactory
import org.junit.BeforeClass
import org.junit.Test
import org.junit.experimental.categories.Category

@Category(AcceptanceCategory::class)
class AwsDatasetTest {

    private val aws: Aws = Aws(
        region = Regions.EU_WEST_1,
        credentialsProvider = DefaultAWSCredentialsProviderChain()
    )
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

    companion object {
        private val workspace = RootWorkspace().currentTask

        @BeforeClass
        @JvmStatic
        fun configureLogs() {
            ConfigurationFactory.setConfigurationFactory(LogConfigurationFactory(workspace))
        }
    }
}
