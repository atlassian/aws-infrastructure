package com.atlassian.performance.tools.awsinfrastructure.api

import com.atlassian.performance.tools.aws.api.StorageLocation
import com.atlassian.performance.tools.awsinfrastructure.api.dataset.S3DatasetPackage
import com.atlassian.performance.tools.infrastructure.api.database.LicenseOverridingMysql
import com.atlassian.performance.tools.infrastructure.api.database.MySqlDatabase
import com.atlassian.performance.tools.infrastructure.api.dataset.Dataset
import com.atlassian.performance.tools.infrastructure.api.dataset.FileArchiver
import com.atlassian.performance.tools.infrastructure.api.dataset.HttpDatasetPackage
import com.atlassian.performance.tools.infrastructure.api.jira.JiraHomePackage
import java.net.URI
import java.time.Duration
import java.time.Duration.ofMinutes

class DatasetCatalogue {

    @Deprecated(message = "Use largeJiraSeven() instead")
    fun largeJira() = largeJiraSeven()

    fun largeJiraSeven(): Dataset {
        val bucketUri = URI("https://s3-eu-west-1.amazonaws.com/jpt-custom-datasets-storage-a008820-datasetbucket-1sjxdtrv5hdhj/")
        return Dataset(
            label = "2M issues, format 7",
            database = MySqlDatabase(
                HttpDatasetPackage(
                    uri = bucketUri.resolve("dataset-d4684761-116b-49ae-9cce-e45cecdcae2a/database.tar.bz2"),
                    downloadTimeout = ofMinutes(17)
                )
            ),
            jiraHomeSource = JiraHomePackage(
                HttpDatasetPackage(
                    uri = bucketUri.resolve("dataset-d4684761-116b-49ae-9cce-e45cecdcae2a/jirahome.tar.bz2"),
                    downloadTimeout = ofMinutes(21)
                )
            )
        )
    }

    fun largeJiraEight(): Dataset {
        val bucketUri = URI("https://s3.eu-central-1.amazonaws.com/jpt-custom-datasets-storage-a008820-datasetbucket-dah44h6l1l8p/")
        return Dataset(
            label = "2M issues, format 8",
            database = MySqlDatabase(
                HttpDatasetPackage(
                    uri = bucketUri.resolve("dataset-631c70d4-084b-455c-9785-b01068b9f07c/database.tar.bz2"),
                    downloadTimeout = ofMinutes(17)
                )
            ),
            jiraHomeSource = JiraHomePackage(
                HttpDatasetPackage(
                    uri = bucketUri.resolve("dataset-631c70d4-084b-455c-9785-b01068b9f07c/jirahome.tar.bz2"),
                    downloadTimeout = ofMinutes(21)
                )
            )
        )
    }

    /**
     * Has more active users than the timebomb license, so on DC, it will refuse to create issues.
     * Use [LicenseOverridingMysql] to supply a bigger license if need be.
     *
     * @since 2.12.0
     */
    fun smallJiraSeven(): Dataset = URI("https://s3-eu-west-1.amazonaws.com/")
        .resolve("jpt-custom-datasets-storage-a008820-datasetbucket-1sjxdtrv5hdhj/")
        .resolve("dataset-f8dba866-9d1b-492e-b76c-f4a78ac3958c/")
        .let { uri ->
            Dataset(
                label = "7k issues JSW 7.2.0",
                database = MySqlDatabase(HttpDatasetPackage(
                    uri = uri.resolve("database.tar.bz2"),
                    downloadTimeout = ofMinutes(6)
                )),
                jiraHomeSource = JiraHomePackage(HttpDatasetPackage(
                    uri = uri.resolve("jirahome.tar.bz2"),
                    downloadTimeout = ofMinutes(6)
                ))
            )
        }

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
