package com.atlassian.performance.tools.awsinfrastructure.api

import com.atlassian.performance.tools.aws.api.StorageLocation
import com.atlassian.performance.tools.awsinfrastructure.api.jira.StoppableNode
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

class CustomDatasetSource private constructor(
    val jiraHome: RemoteLocation,
    val database: RemoteLocation,
    private val jiraHomeTimeouts: Timeouts,
    private val databaseTimeouts: Timeouts,
    private val nodes: List<StoppableNode>
) {

    object FileNames {
        const val DATABASE = "database"
        const val JIRAHOME = "jirahome"
    }

    private val logger: Logger = LogManager.getLogger(this::class.java)

    fun toJson(): JsonObject {
        return Json.createObjectBuilder()
            .add("jiraHome", jiraHome.toJson())
            .add("database", database.toJson())
            .add("nodes", Json.createArrayBuilder().apply {
                nodes.map { it.toJson() }.forEach { add(it) }
            }.build())
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

        stopJira()
        stopDockerContainers(database.host)

        val jiraHomeUpload = executor.submitWithLogContext("jiraHome") {
            val renamed = jiraHome.move(FileNames.JIRAHOME, Duration.ofMinutes(1))
            try {
                renamed
                    .archive(jiraHomeTimeouts.archive)
                    .upload(location, jiraHomeTimeouts.upload)
            } finally {
                renamed.move(jiraHome.location, jiraHomeTimeouts.move)
            }
        }
        val databaseUpload = executor.submitWithLogContext("database") {
            val renamed = database.move(FileNames.DATABASE, Duration.ofMinutes(1))
            try {
                renamed
                    .archive(databaseTimeouts.archive)
                    .upload(location, databaseTimeouts.upload)
            } finally {
                renamed.move(database.location, databaseTimeouts.move)
            }
        }

        jiraHomeUpload.get()
        databaseUpload.get()

        executor.shutdownNow()
        val locationSnippet = location.toKotlinCodeSnippet()
        logger.info("Dataset saved. You can use it via `DatasetCatalogue().custom($locationSnippet)`")
        return DatasetCatalogue().custom(location)
    }

    private fun stopJira() {
        nodes.forEach { it.stop() }
    }

    private fun stopDockerContainers(host: SshHost) {
        Ssh(host, connectivityPatience = 4).newConnection().use { it.execute("sudo docker stop \$(sudo docker ps -aq)") }
    }

    override fun toString(): String {
        return "CustomDatasetSource(jiraHome=$jiraHome, database=$database)"
    }

    private class Timeouts(
        val archive: Duration,
        val upload: Duration,
        val move: Duration
    )

    class Builder(
        private var jiraHome: RemoteLocation,
        private var database: RemoteLocation,
        private var nodes: List<StoppableNode>
    ) {
        private var jiraHomeArchiveTimeout: Duration = Duration.ofMinutes(25)
        private var jiraHomeUploadTimeout: Duration = Duration.ofMinutes(10)
        private var jiraHomeMoveTimeout: Duration = Duration.ofMinutes(1)
        private var databaseArchiveTimeout: Duration = Duration.ofMinutes(25)
        private var databaseUploadTimeout: Duration = Duration.ofMinutes(10)
        private var databaseMoveTimeout: Duration = Duration.ofMinutes(1)

        constructor(json: JsonObject) : this(
            jiraHome = RemoteLocation(json.getJsonObject("jiraHome")),
            database = RemoteLocation(json.getJsonObject("database")),
            nodes = json.getJsonArray("nodes").map { StoppableNode.Builder(it.asJsonObject()).build() }
        )


        fun jiraHome(jiraHome: RemoteLocation) = apply { this.jiraHome = jiraHome }
        fun database(database: RemoteLocation) = apply { this.database = database }
        fun jiraHomeArchiveTimeout(jiraHomeArchiveTimeout: Duration) =
            apply { this.jiraHomeArchiveTimeout = jiraHomeArchiveTimeout }

        fun jiraHomeUploadTimeout(jiraHomeUploadTimeout: Duration) =
            apply { this.jiraHomeUploadTimeout = jiraHomeUploadTimeout }

        fun jiraHomeMoveTimeout(jiraHomeMoveTimeout: Duration) =
            apply { this.jiraHomeMoveTimeout = jiraHomeMoveTimeout }

        fun databaseArchiveTimeout(databaseArchiveTimeout: Duration) =
            apply { this.databaseArchiveTimeout = databaseArchiveTimeout }

        fun databaseUploadTimeout(databaseUploadTimeout: Duration) =
            apply { this.databaseUploadTimeout = databaseUploadTimeout }

        fun databaseMoveTimeout(databaseMoveTimeout: Duration) =
            apply { this.databaseMoveTimeout = databaseMoveTimeout }

        fun nodes(nodes: List<StoppableNode>) = apply { this.nodes = nodes }

        fun build() = CustomDatasetSource(
            jiraHome,
            database,
            Timeouts(
                jiraHomeArchiveTimeout,
                jiraHomeUploadTimeout,
                jiraHomeMoveTimeout
            ),
            Timeouts(
                databaseArchiveTimeout,
                databaseUploadTimeout,
                databaseMoveTimeout
            ),
            nodes
        )
    }
}
