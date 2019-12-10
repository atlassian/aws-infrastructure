package com.atlassian.performance.tools.awsinfrastructure.api.jira

import com.atlassian.performance.tools.awsinfrastructure.api.storage.S3Artifact
import com.atlassian.performance.tools.awsinfrastructure.api.storage.S3TarGzDistribution
import com.atlassian.performance.tools.infrastructure.api.distribution.ProductDistribution
import com.atlassian.performance.tools.ssh.api.SshConnection
import java.time.Duration

/**
 * Distributes dev versions, e.g. Jira built from source code.
 * Requires S3 credentials, therefore it's unsuitable for non-Atlassian users.
 *
 * @since 2.20.0
 */
class JiraSoftwareDevDistribution(
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
                region = "eu-central-1",
                bucketName = "jira-server-jpt",
                archivesLocation = "software/jira/downloads",
                archiveName = "atlassian-jira-software-$version.tar.gz"
            ),
            unpackTimeout
        )
        return distribution.install(ssh, destination)
    }
}
