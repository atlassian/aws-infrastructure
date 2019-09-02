package com.atlassian.performance.tools.awsinfrastructure.aws

import com.atlassian.performance.tools.awsinfrastructure.api.storage.S3Artifact
import com.atlassian.performance.tools.infrastructure.api.distribution.ProductDistribution
import com.atlassian.performance.tools.ssh.api.SshConnection
import org.apache.logging.log4j.Level
import java.time.Duration

internal class S3Distribution(
    private val s3Artifact: S3Artifact,
    private val unpackTimeout: Duration
) : ProductDistribution {

    override fun install(
        ssh: SshConnection,
        destination: String
    ): String {
        s3Artifact.download(ssh, destination)
        unpack(ssh, s3Artifact.archiveName, destination)
        val unpackedProductName = getUnpackedProductName(ssh, s3Artifact.archiveName, destination)
        return "$destination/$unpackedProductName"
    }

    private fun unpack(
        ssh: SshConnection,
        archiveName: String,
        destination: String
    ) {
        ssh.execute(
            cmd = "tar -xzf $destination/$archiveName --directory $destination",
            timeout = unpackTimeout,
            stdout = Level.TRACE,
            stderr = Level.TRACE
        )
    }

    private fun getUnpackedProductName(
        ssh: SshConnection,
        archiveName: String,
        destination: String
    ): String = ssh
        .execute(
            cmd = "tar -tf $destination/$archiveName --directory $destination | head -n 1",
            timeout = unpackTimeout
        )
        .output
        .split("/")
        .first()
}