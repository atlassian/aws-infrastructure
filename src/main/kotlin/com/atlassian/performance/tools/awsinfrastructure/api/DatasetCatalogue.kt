package com.atlassian.performance.tools.awsinfrastructure.api

import com.amazonaws.regions.Regions.EU_CENTRAL_1
import com.atlassian.performance.tools.aws.StorageLocation
import com.atlassian.performance.tools.awsinfrastructure.S3DatasetPackage
import com.atlassian.performance.tools.infrastructure.api.database.MySqlDatabase
import com.atlassian.performance.tools.infrastructure.api.dataset.Dataset
import com.atlassian.performance.tools.infrastructure.api.dataset.FileArchiver
import com.atlassian.performance.tools.infrastructure.api.jira.JiraHomePackage
import java.net.URI
import java.time.Duration
import java.time.Duration.ofMinutes

class DatasetCatalogue {

    fun largeJira() = custom(
        location = StorageLocation(
            URI("s3://jpt-custom-datasets-storage-a008820-datasetbucket-dah44h6l1l8p/dataset-6aa68633-3655-4c51-b344-d8a13cfb3fd0"),
            EU_CENTRAL_1
        ),
        label = "2M issues",
        jiraHomeDownload = ofMinutes(13)
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