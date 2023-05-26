package com.atlassian.performance.tools.awsinfrastructure.api.jira

import com.atlassian.performance.tools.aws.api.StorageLocation
import com.atlassian.performance.tools.awsinfrastructure.api.aws.AwsCli
import com.atlassian.performance.tools.infrastructure.api.jira.JiraHomeSource
import com.atlassian.performance.tools.ssh.api.SshConnection
import java.time.Duration
import java.util.function.Supplier

class AvatarsInS3(
    private val delegate: JiraHomeSource,
    private val s3StorageProvider: Supplier<StorageLocation>,
    private val filestoreConfigAppender: FilestoreConfigAppender
) : JiraHomeSource {
    private val associationTarget = "avatars"

    override fun download(
        ssh: SshConnection
    ) = delegate
        .download(ssh)
        .also { jiraHomeLocation ->
            val s3Bucket = s3StorageProvider.get()
            uploadToS3(ssh, jiraHomeLocation, s3Bucket)
            updateFilestoreConfig(ssh, jiraHomeLocation, s3Bucket)
        }

    private fun uploadToS3(
        ssh: SshConnection,
        jiraHomeLocation: String,
        s3Bucket: StorageLocation
    ) {
        AwsCli("2.9.12").ensureAwsCli(ssh)
        val avatarsLocation = "$jiraHomeLocation/data/avatars"
        val s3AvatarsUri = s3Bucket.uri.resolve("avatars")
        ssh.execute("aws configure set default.s3.max_concurrent_requests 300")
        ssh.execute(
            cmd = "AWS_RETRY_MODE=standard AWS_MAX_ATTEMPTS=10 aws s3 sync $avatarsLocation $s3AvatarsUri",
            timeout = Duration.ofMinutes(10)
        )
    }

    private fun updateFilestoreConfig(
        ssh: SshConnection,
        jiraHomeLocation: String,
        s3Bucket: StorageLocation
    ) {
        val filestoreConfigLocation = "$jiraHomeLocation/filestore-config.xml"
        filestoreConfigAppender.append(ssh, filestoreConfigLocation, s3Bucket, associationTarget)
    }

    interface FilestoreConfigAppender {
        fun append(
            ssh: SshConnection,
            filestoreConfigLocation: String,
            s3Bucket: StorageLocation,
            target: String
        )
    }
}