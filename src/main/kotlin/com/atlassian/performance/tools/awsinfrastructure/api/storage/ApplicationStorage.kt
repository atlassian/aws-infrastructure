package com.atlassian.performance.tools.awsinfrastructure.api.storage

import com.atlassian.performance.tools.awsinfrastructure.api.aws.AwsCli
import com.atlassian.performance.tools.ssh.api.SshConnection

@Deprecated("Use `com.atlassian.performance.tools.infrastructure.api.storage.ProductDistribution` instead.")
interface ApplicationStorage {
    val possibleLocations: List<S3Artifact>

    fun download(
        ssh: SshConnection,
        target: String
    ): String {
        AwsCli().ensureAwsCli(ssh)

        return possibleLocations
            .asSequence()
            .firstOrNull { it.download(ssh, target).isSuccessful() }
            ?.archiveName
            ?: throw Exception("Cannot find Jira artifact in any of $possibleLocations")
    }
}