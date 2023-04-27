package com.atlassian.performance.tools.awsinfrastructure.api.aws

import com.atlassian.performance.tools.aws.api.StorageLocation
import com.atlassian.performance.tools.infrastructure.api.os.Ubuntu
import com.atlassian.performance.tools.ssh.api.SshConnection
import org.apache.logging.log4j.Level
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

/**
 * The AWS CLI via SSH.
 *
 * @since 2.15.0
 */
class AwsCli (val cliVersion: String = "2.9.12") {
    private val versionRegex = Regex("""([0-9]+)\.[0-9]+\.[0-9]+""")
    init {
        require( versionRegex.matches(cliVersion)) {
            "$cliVersion is not a valid aws cli version string."
        }
    }

    private companion object {
        private val LOCKS = ConcurrentHashMap<String, Any>()
    }

    fun ensureAwsCli(ssh: SshConnection) {
        val lock = LOCKS.computeIfAbsent(ssh.getHost().ipAddress) { Object() }
        synchronized(lock) {
            val awsCliExecutionResult = ssh.safeExecute("aws --version", Duration.ofSeconds(30), Level.TRACE, Level.TRACE)
            if (awsCliExecutionResult.isSuccessful()) {
                val combinedOutput = "${awsCliExecutionResult.output}${awsCliExecutionResult.errorOutput}"
                require(combinedOutput.contains("aws-cli/$cliVersion")) {
                    "Aws Cli version $cliVersion requested but different version is already installed: '${combinedOutput}'."
                }
            } else {
                Ubuntu().install(ssh, listOf("zip", "python"), Duration.ofMinutes(3))
                val majorVersion = versionRegex.find(cliVersion)?.groupValues?.get(1)
                when (majorVersion) {
                    "1" -> installV1Cli(ssh)
                    else -> installV2Cli(ssh)
                }
            }
        }
    }

    private fun installV1Cli(ssh: SshConnection) {
        ssh.execute(
            cmd = "curl --silent https://s3.amazonaws.com/aws-cli/awscli-bundle-$cliVersion.zip -o awscli-bundle.zip",
            timeout = Duration.ofSeconds(50)
        )
        ssh.execute("unzip -n -q awscli-bundle.zip")
        ssh.execute(
            cmd = "sudo ./awscli-bundle/install -i /usr/local/aws -b /usr/local/bin/aws",
            timeout = Duration.ofSeconds(60)
        )
    }

    private fun installV2Cli(ssh: SshConnection) {
        // Instructions from https://docs.aws.amazon.com/cli/latest/userguide/getting-started-version.html
        ssh.execute(
            cmd="curl --silent https://awscli.amazonaws.com/awscli-exe-linux-x86_64-$cliVersion.zip -o awscliv2.zip",
            timeout = Duration.ofSeconds(50)
        )
        ssh.execute("unzip -n -q awscliv2.zip")
        ssh.execute(
            cmd = "sudo ./aws/install -i /usr/local/aws-cli -b /usr/local/bin",
            timeout = Duration.ofSeconds(60)
        )
    }

    fun download(
        location: StorageLocation,
        ssh: SshConnection,
        target: String,
        timeout: Duration = Duration.ofSeconds(30)
    ) {
        ensureAwsCli(ssh)
        ssh.execute(
            "aws s3 sync --only-show-errors --region=${location.regionName} ${location.uri} $target",
            timeout
        )
    }

    fun upload(
        location: StorageLocation,
        ssh: SshConnection,
        source: String,
        timeout: Duration
    ) {
        ensureAwsCli(ssh)
        ssh.execute(
            "aws s3 sync --only-show-errors --region=${location.regionName} $source ${location.uri}",
            timeout
        )
    }


    fun downloadFile(
        location: StorageLocation,
        ssh: SshConnection,
        file: String,
        target: String,
        timeout: Duration
    ) {
        ssh.execute(
            downloadFileCommand(location, ssh, file, target),
            timeout
        )
    }

    fun downloadFileCommand(
        location: StorageLocation,
        ssh: SshConnection,
        file: String,
        target: String
    ): String {
        ensureAwsCli(ssh)
        return "aws s3 cp --only-show-errors --region=${location.regionName} ${location.uri}/$file $target"
    }

    fun uploadFile(
        location: StorageLocation,
        ssh: SshConnection,
        source: String,
        timeout: Duration
    ) {
        ensureAwsCli(ssh)
        ssh.execute(
            "aws s3 cp --only-show-errors --region=${location.regionName} $source ${location.uri}/$source",
            timeout
        )
    }
}
