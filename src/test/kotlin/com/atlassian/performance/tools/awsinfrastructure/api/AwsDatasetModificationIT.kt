package com.atlassian.performance.tools.awsinfrastructure.api

import com.amazonaws.regions.Regions
import com.atlassian.performance.tools.aws.api.StorageLocation
import com.atlassian.performance.tools.awsinfrastructure.IntegrationTestRuntime.aws
import com.atlassian.performance.tools.awsinfrastructure.IntegrationTestRuntime.taskWorkspace
import com.atlassian.performance.tools.infrastructure.api.dataset.Dataset
import com.atlassian.performance.tools.ssh.api.Ssh
import org.junit.Test
import java.net.URI
import java.time.Duration.ofMinutes

class AwsDatasetModificationIT {

    private val workspace = taskWorkspace.isolateTest(javaClass.simpleName)
    private val sourceDataset = smallJiraSeven()

    @Test
    fun shouldRemoveBackups() {
        val transformation = object : DatasetTransformation {
            override fun transform(infrastructure: Infrastructure<*>) {
                val jiraHome = infrastructure.jira.jiraHome
                val backupPath = "${jiraHome.location}/export"
                Ssh(jiraHome.host, connectivityPatience = 4)
                    .newConnection()
                    .use { ssh ->
                        val listCommand = "ls -lh $backupPath"
                        val listOutput = ssh.execute(listCommand).output
                        println("$ $listCommand\n$listOutput")
                        ssh.execute("rm -r $backupPath")
                    }
            }
        }
        val modification = AwsDatasetModification.Builder(
            aws = aws,
            dataset = sourceDataset,
            transformation = transformation
        )
            .workspace(workspace)
            .build()

        modification.modify()
    }

    private fun smallJiraSeven(): Dataset = DatasetCatalogue().custom(
        location = StorageLocation(
            URI("s3://jpt-custom-datasets-storage-a008820-datasetbucket-1sjxdtrv5hdhj/")
                .resolve("af4c7d3b-925c-464c-ab13-79f615158316"),
            Regions.EU_WEST_1
        ),
        label = "7k issues",
        databaseDownload = ofMinutes(5),
        jiraHomeDownload = ofMinutes(5)
    )
}
