package com.atlassian.performance.tools.awsinfrastructure.storage

import com.atlassian.performance.tools.awsinfrastructure.AwsCli
import com.atlassian.performance.tools.ssh.SshConnection

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