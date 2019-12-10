package com.atlassian.performance.tools.awsinfrastructure.api.jira

import com.atlassian.performance.tools.awsinfrastructure.api.storage.S3Artifact
import com.atlassian.performance.tools.awsinfrastructure.api.storage.S3TarGzDistribution
import com.atlassian.performance.tools.infrastructure.api.distribution.ProductDistribution
import com.atlassian.performance.tools.ssh.api.SshConnection
import java.time.Duration

/**
 * Distributes internal versions, e.g. milestones.
 * Requires S3 credentials, therefore it's unsuitable for non-Atlassian users.
 *
 * @since 2.15.0
 */
class JiraSoftwareInternalDistribution(
    private val version: String,
    private val unpackTimeout: Duration
) : ProductDistribution {

    constructor(
        version: String
    ) : this(
        version = version,
        unpackTimeout = Duration.ofSeconds(90)
    )

    override fun install(
        ssh: SshConnection,
        destination: String
    ): String {
        val distribution = S3TarGzDistribution(
            S3Artifact(
                region = "us-east-1",
                bucketName = "downloads-internal-us-east-1",
                archivesLocation = "private/jira/$version",
                archiveName = "atlassian-jira-software-$version-standalone.tar.gz"
            ),
            unpackTimeout
        )
        return distribution.install(ssh, destination)
    }
}
