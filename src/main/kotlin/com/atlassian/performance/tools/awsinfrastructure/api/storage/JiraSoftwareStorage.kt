package com.atlassian.performance.tools.awsinfrastructure.api.storage

class JiraSoftwareStorage(
    version: String
) : ApplicationStorage {
    private val archiveName = "atlassian-jira-software-$version.tar.gz"

    override val possibleLocations = listOf(
        S3Artifact(
            region = "us-east-1",
            bucketName = "downloads-public-us-east-1",
            archivesLocation = "software/jira/downloads",
            archiveName = archiveName
        ),
        S3Artifact(
            region = "eu-central-1",
            bucketName = "jira-server-jpt",
            archivesLocation = "software/jira/downloads",
            archiveName = archiveName
        ),
        S3Artifact(
            region = "us-east-1",
            bucketName = "downloads-internal-us-east-1",
            archivesLocation = "private/jira/$version",
            archiveName = "atlassian-jira-software-$version-standalone.tar.gz"
        )
    )
}

