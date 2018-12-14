package com.atlassian.performance.tools.awsinfrastructure.api

import com.amazonaws.regions.Regions
import com.atlassian.performance.tools.aws.api.StorageLocation
import com.atlassian.performance.tools.awsinfrastructure.S3DatasetPackage
import com.atlassian.performance.tools.infrastructure.api.database.MySqlDatabase
import com.atlassian.performance.tools.infrastructure.api.dataset.Dataset
import com.atlassian.performance.tools.infrastructure.api.dataset.FileArchiver
import com.atlassian.performance.tools.infrastructure.api.jira.JiraHomePackage
import java.net.URI
import java.time.Duration
import java.time.Duration.ofMinutes

class DatasetCatalogue {

    @Deprecated(message = "Use largeJiraSeven() instead")
    fun largeJira() = largeJiraSeven()

    fun largeJiraSeven(): Dataset = custom(
        location = StorageLocation(
            URI("s3://jpt-custom-datasets-storage-a008820-datasetbucket-1sjxdtrv5hdhj/")
                .resolve("dataset-d4684761-116b-49ae-9cce-e45cecdcae2a"),
            Regions.EU_WEST_1
        ),
        label = "2M issues, format 7",
        databaseDownload = ofMinutes(17),
        jiraHomeDownload = ofMinutes(21)
    )

    fun largeJiraEight(): Dataset = custom(
        location = StorageLocation(
            URI("s3://jpt-custom-datasets-storage-a008820-datasetbucket-dah44h6l1l8p/")
                .resolve("dataset-631c70d4-084b-455c-9785-b01068b9f07c"),
            Regions.EU_CENTRAL_1
        ),
        label = "2M issues, format 8",
        databaseDownload = ofMinutes(17),
        jiraHomeDownload = ofMinutes(21)
    )

    fun custom(
        location: StorageLocation,
        label: String = "custom",
        databaseDownload: Duration = ofMinutes(10),
        jiraHomeDownload: Duration = ofMinutes(10)
    ): Dataset {
        val archiver = FileArchiver()
        return Dataset(
            label = label,
            database = MySqlDatabase(
                S3DatasetPackage(
                    artifactName = archiver.zippedName(CustomDatasetSource.FileNames.DATABASE),
                    location = location,
                    unpackedPath = CustomDatasetSource.FileNames.DATABASE,
                    downloadTimeout = databaseDownload
                )
            ),
            jiraHomeSource = JiraHomePackage(
                S3DatasetPackage(
                    artifactName = archiver.zippedName(CustomDatasetSource.FileNames.JIRAHOME),
                    location = location,
                    unpackedPath = CustomDatasetSource.FileNames.JIRAHOME,
                    downloadTimeout = jiraHomeDownload
                )
            )
        )
    }
}