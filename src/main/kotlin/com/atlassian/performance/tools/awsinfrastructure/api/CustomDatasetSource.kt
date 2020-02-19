package com.atlassian.performance.tools.awsinfrastructure.api

import com.atlassian.performance.tools.aws.api.StorageLocation
import com.atlassian.performance.tools.concurrency.api.submitWithLogContext
import com.atlassian.performance.tools.infrastructure.api.dataset.Dataset
import com.atlassian.performance.tools.ssh.api.Ssh
import com.atlassian.performance.tools.ssh.api.SshHost
import com.google.common.util.concurrent.ThreadFactoryBuilder
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.time.Duration
import java.util.concurrent.Executors
import javax.json.Json
import javax.json.JsonObject

class CustomDatasetSource(
    val jiraHome: RemoteLocation,
    val database: RemoteLocation
) {
    object FileNames {
        const val DATABASE = "database"
        const val JIRAHOME = "jirahome"
    }

    private val logger: Logger = LogManager.getLogger(this::class.java)

    constructor(json: JsonObject) : this(
        jiraHome = RemoteLocation(json.getJsonObject("jiraHome")),
        database = RemoteLocation(json.getJsonObject("database"))
    )

    fun toJson(): JsonObject {
        return Json.createObjectBuilder()
            .add("jiraHome", jiraHome.toJson())
            .add("database", database.toJson())
            .build()
    }

    fun storeInS3(
        location: StorageLocation
    ) {
        store(location)
    }

    internal fun store(
        location: StorageLocation
    ): Dataset {
        logger.info("Uploading dataset to '$location'...")
        val executor = Executors.newFixedThreadPool(
            3,
            ThreadFactoryBuilder()
                .setNameFormat("s3-upload-thread-%d")
                .build()
        )

        stopJira(jiraHome.host)
        stopDockerContainers(database.host)

        val jiraHomeUpload = executor.submitWithLogContext("jiraHome") {
            val renamed = jiraHome.move(FileNames.JIRAHOME, Duration.ofMinutes(1))
            try {
                renamed
                    .archive(Duration.ofMinutes(25))
                    .upload(location, Duration.ofMinutes(10))
            } finally {
                renamed.move(jiraHome.location, Duration.ofMinutes(1))
            }
        }
        val databaseUpload = executor.submitWithLogContext("database") {
            val renamed = database.move(FileNames.DATABASE, Duration.ofMinutes(1))
            try {
                renamed
                    .archive(Duration.ofMinutes(25))
                    .upload(location, Duration.ofMinutes(10))
            } finally {
                renamed.move(database.location, Duration.ofMinutes(1))
            }
        }

        jiraHomeUpload.get()
        databaseUpload.get()

        executor.shutdownNow()
        val locationSnippet = location.toKotlinCodeSnippet()
        logger.info("Dataset saved. You can use it via `DatasetCatalogue().custom($locationSnippet)`")
        return DatasetCatalogue().custom(location)
    }

    private fun stopJira(host: SshHost) {
        val shutdownTomcat = "echo SHUTDOWN | nc localhost 8005"
        val waitForNoJiraProcess = "while pgrep -f jira; do sleep 5; done"
        Ssh(host, connectivityPatience = 4).newConnection().use {
            it.safeExecute("$shutdownTomcat && $waitForNoJiraProcess", Duration.ofMinutes(3))
        }
    }

    private fun stopDockerContainers(host: SshHost) {
        Ssh(host, connectivityPatience = 4).newConnection().use { it.safeExecute("docker stop \$(docker ps -aq)") }
    }

    override fun toString(): String {
        return "CustomDatasetSource(jiraHome=$jiraHome, database=$database)"
    }
}