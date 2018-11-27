package com.atlassian.performance.tools.awsinfrastructure.api

import com.atlassian.performance.tools.aws.api.StorageLocation
import com.atlassian.performance.tools.awsinfrastructure.AwsCli
import com.atlassian.performance.tools.infrastructure.api.dataset.FileArchiver
import com.atlassian.performance.tools.ssh.api.Ssh
import com.atlassian.performance.tools.ssh.api.SshHost
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.nio.file.Path
import java.time.Duration
import javax.json.Json
import javax.json.JsonObject

data class RemoteLocation(val host: SshHost, val location: String) {

    private val logger: Logger = LogManager.getLogger(this::class.java)

    constructor(json: JsonObject) : this(
        host = SshHost(json.getJsonObject("host")),
        location = json.getString("location")
    )

    fun toJson(): JsonObject {
        return Json.createObjectBuilder()
            .add("host", host.toJson())
            .add("location", location)
            .build()
    }

    fun move(destination: String, timeout: Duration): RemoteLocation {
        if (location != destination) {
            Ssh(host, connectivityPatience = 4).newConnection().use {
                it.execute("mv $location $destination", timeout)
            }
        }
        return RemoteLocation(host, destination)
    }

    fun archive(timeout: Duration): RemoteLocation {
        logger.info("Archiving $location...")
        val destination = Ssh(host, connectivityPatience = 4).newConnection().use {
            FileArchiver().zip(it, location, timeout)
        }
        logger.info("Archiving $location complete")
        return RemoteLocation(host, destination)
    }

    fun upload(storage: StorageLocation, timeout: Duration) {
        logger.info("Uploading $location...")
        Ssh(host, connectivityPatience = 4).newConnection().use { AwsCli().uploadFile(storage, it, location, timeout) }
        logger.info("Uploading $location complete")
    }

    fun download(
        localDestination: Path
    ) {
        Ssh(host, connectivityPatience = 4).newConnection().use {
            it.download(
                remoteSource = location,
                localDestination = localDestination
            )
        }
    }
}
