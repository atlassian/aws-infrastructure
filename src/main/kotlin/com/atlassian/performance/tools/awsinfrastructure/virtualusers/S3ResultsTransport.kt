package com.atlassian.performance.tools.awsinfrastructure.virtualusers

import com.atlassian.performance.tools.aws.Storage
import com.atlassian.performance.tools.awsinfrastructure.AwsCli
import com.atlassian.performance.tools.infrastructure.api.virtualusers.ResultsTransport
import com.atlassian.performance.tools.ssh.SshConnection
import java.time.Duration

class S3ResultsTransport(
    private val results: Storage
) : ResultsTransport {

    override fun transportResults(
        targetDirectory: String,
        sshConnection: SshConnection
    ) {
        AwsCli().upload(results.location, sshConnection, targetDirectory, Duration.ofMinutes(10))
    }

    override fun toString(): String {
        return "S3ResultsTransport(results=$results)"
    }
}