package com.atlassian.performance.tools.awsinfrastructure

import com.atlassian.performance.tools.infrastructure.api.distribution.ProductDistribution
import com.atlassian.performance.tools.ssh.api.SshConnection
import java.time.Duration

/**
 *  Provides compatibility layer to use `AppStorage` as `ProductDistribution`.
 *  We can get rid of the class as soon as we remove AppStorage from the API.
 */
internal class ApplicationStorageWrapper(
    @Suppress("DEPRECATION") private val applicationStorage: com.atlassian.performance.tools.awsinfrastructure.api.storage.ApplicationStorage
) : ProductDistribution {
    private val DELIMITER = "/"
    override fun install(ssh: SshConnection, destination: String): String {
        val archiveName = applicationStorage.download(ssh, destination)
        unpack(ssh, archiveName, destination)
        return "$destination/${getUnpackedProductName(ssh, archiveName, destination)}"
    }

    private fun getUnpackedProductName(
        connection: SshConnection,
        archiveName: String,
        destination: String
    ): String {
        return connection
            .execute(
                "tar -tf $destination/$archiveName --directory $destination | head -n 1",
                timeout = Duration.ofMinutes(1)
            )
            .output
            .split(DELIMITER)
            .first()
    }

    private fun unpack(ssh: SshConnection, archiveName: String, destination: String) {
        ssh.execute("tar -xzf $destination/$archiveName --directory $destination", Duration.ofMinutes(1))
    }

}