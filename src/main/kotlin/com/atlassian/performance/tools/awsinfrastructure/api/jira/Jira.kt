package com.atlassian.performance.tools.awsinfrastructure.api.jira

import com.atlassian.performance.tools.awsinfrastructure.api.CustomDatasetSource
import com.atlassian.performance.tools.awsinfrastructure.api.RemoteLocation
import com.atlassian.performance.tools.concurrency.api.submitWithLogContext
import com.atlassian.performance.tools.infrastructure.api.MeasurementSource
import com.atlassian.performance.tools.infrastructure.api.jvm.jmx.JmxClient
import com.atlassian.performance.tools.jvmtasks.api.TaskScope.task
import com.google.common.util.concurrent.ThreadFactoryBuilder
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.net.URI
import java.util.concurrent.Callable
import java.util.concurrent.Executors

class Jira private constructor(
    private val nodes: List<StartedNode>,
    val jiraHome: RemoteLocation,
    val database: RemoteLocation,
    val address: URI,
    val jmxClients: List<JmxClient> = emptyList()
) : MeasurementSource {
    private val logger: Logger = LogManager.getLogger(this::class.java)

    override fun gatherResults() {
        if (nodes.isEmpty()) {
            logger.warn("No Jira nodes known to JPT, not downloading node results")
            return
        }
        val executor = Executors.newFixedThreadPool(
            nodes.size.coerceAtMost(4),
            ThreadFactoryBuilder()
                .setNameFormat("results-gathering-thread-%d")
                .build()
        )
        nodes.map { executor.submitWithLogContext("gather $it") { it.gatherResults() } }
            .forEach { it.get() }
        task("gather analytics", Callable { nodes.firstOrNull()?.gatherAnalyticLogs() })
        executor.shutdownNow()
    }

    fun toDatasetSource(): CustomDatasetSource.Builder {
        return CustomDatasetSource.Builder(
            jiraHome = jiraHome,
            database = database,
            nodes = nodes.map { it.toStoppableNode() }
        )
    }

    override fun toString() = "Jira(address=$address)"

    class Builder(
        private val nodes: List<StartedNode>,
        private val jiraHome: RemoteLocation,
        private val database: RemoteLocation,
        private val address: URI
    ) {
        private var jmxClients: List<JmxClient> = emptyList()
        fun jmxClients(jmxClients: List<JmxClient>) = apply { this.jmxClients = jmxClients }

        fun build() = Jira(
            nodes = nodes,
            jiraHome = jiraHome,
            database = database,
            address = address,
            jmxClients = jmxClients
        )
    }
}
