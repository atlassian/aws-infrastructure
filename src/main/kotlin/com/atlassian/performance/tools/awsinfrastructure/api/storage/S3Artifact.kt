package com.atlassian.performance.tools.awsinfrastructure.api.storage

import com.atlassian.performance.tools.awsinfrastructure.api.aws.AwsCli
import com.atlassian.performance.tools.ssh.api.SshConnection
import java.net.URI
import java.time.Duration

class S3Artifact(
    private val region: String,
    bucketName: String,
    archivesLocation: String,
    val archiveName: String
) {
    private val uri = URI.create("s3://$bucketName/$archivesLocation/$archiveName")

    fun download(
        ssh: SshConnection,
        target: String
    ): SshConnection.SshResult {
        AwsCli().ensureAwsCli(ssh)
        return ssh.safeExecute(
            cmd = "aws s3 cp --only-show-errors --region=$region $uri $target",
            timeout = Duration.ofMinutes(2)
        )
    }

    override fun toString(): String {
        return "S3Artifact(region='$region', uri=$uri)"
    }
}