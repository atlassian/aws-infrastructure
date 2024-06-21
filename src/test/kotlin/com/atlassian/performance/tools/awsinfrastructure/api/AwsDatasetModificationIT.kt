package com.atlassian.performance.tools.awsinfrastructure.api

import com.amazonaws.regions.Regions
import com.atlassian.performance.tools.aws.api.Investment
import com.atlassian.performance.tools.aws.api.StorageLocation
import com.atlassian.performance.tools.awsinfrastructure.IntegrationTestRuntime.aws
import com.atlassian.performance.tools.awsinfrastructure.IntegrationTestRuntime.taskWorkspace
import com.atlassian.performance.tools.awsinfrastructure.api.dataset.DatasetHost
import com.atlassian.performance.tools.awsinfrastructure.api.hardware.C5NineExtraLargeEphemeral
import com.atlassian.performance.tools.awsinfrastructure.api.jira.StandaloneFormula
import com.atlassian.performance.tools.awsinfrastructure.api.virtualusers.AbsentVirtualUsersFormula
import com.atlassian.performance.tools.infrastructure.api.dataset.Dataset
import com.atlassian.performance.tools.infrastructure.api.distribution.PublicJiraSoftwareDistribution
import com.atlassian.performance.tools.infrastructure.api.jira.JiraNodeConfig
import com.atlassian.performance.tools.infrastructure.api.jvm.OpenJDK
import com.atlassian.performance.tools.ssh.api.Ssh
import org.junit.Test
import java.net.URI
import java.time.Duration.ofMinutes
import java.util.function.Consumer

class AwsDatasetModificationIT {

    private val workspace = taskWorkspace.isolateTest(javaClass.simpleName)
    private val sourceDataset = smallJiraSeven()

    @Test
    fun shouldRemoveBackups() {
        val transformation = Consumer<Infrastructure<*>> { infrastructure ->
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
        // avoid unstable default JDK from StandaloneFormula.Builder/JiraNodeConfig.Builder
        // TODO update the default DatasetHost after 3.2.0 release
        val stableDatasetHost = DatasetHost {
            InfrastructureFormula
                .Builder(
                    aws = aws,
                    virtualUsersFormula = AbsentVirtualUsersFormula()
                )
                .investment(
                    investment = Investment(
                        useCase = "Generic purpose dataset modification",
                        lifespan = ofMinutes(50)
                    )
                )
                .jiraFormula(
                    StandaloneFormula
                        .Builder(PublicJiraSoftwareDistribution("8.0.0"), it.jiraHomeSource, it.database)
                        .config(JiraNodeConfig.Builder().versionedJdk(OpenJDK()).build())
                        .computer(C5NineExtraLargeEphemeral())
                        .build()
                )
                .build()
        }
        val modification = AwsDatasetModification.Builder(
            aws = aws,
            dataset = sourceDataset
        )
            .host(stableDatasetHost)
            .workspace(workspace)
            .onlineTransformation(transformation)
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
