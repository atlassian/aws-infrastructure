package com.atlassian.performance.tools.awsinfrastructure.api.storage

@Suppress("DEPRECATION")
@Deprecated(message = "Use `com.atlassian.performance.tools.infrastructure.api.distribution.PublicJiraServiceDeskDistribution` instead.")
class JiraServiceDeskStorage(
    version: String
) : ApplicationStorage {

    override val possibleLocations = listOf(
        S3Artifact(
            region = "us-east-1",
            bucketName = "downloads-public-us-east-1",
            archivesLocation = "software/jira/downloads",
            archiveName = "atlassian-servicedesk-$version.tar.gz"
        ),
        S3Artifact(
            region = "eu-central-1",
            bucketName = "jira-server-jpt",
            archivesLocation = "software/jira/downloads",
            archiveName = "atlassian-jira-servicedesk-$version.tar.gz"
        )
    )
}
