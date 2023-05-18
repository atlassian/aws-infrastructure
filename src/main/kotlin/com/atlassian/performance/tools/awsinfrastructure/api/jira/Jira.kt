package com.atlassian.performance.tools.awsinfrastructure.api.jira

import com.atlassian.performance.tools.awsinfrastructure.api.CustomDatasetSource
import com.atlassian.performance.tools.awsinfrastructure.FailSafeRunnable
import com.atlassian.performance.tools.awsinfrastructure.api.RemoteLocation
import com.atlassian.performance.tools.concurrency.api.submitWithLogContext
import com.atlassian.performance.tools.infrastructure.api.MeasurementSource
import com.atlassian.performance.tools.infrastructure.api.jvm.jmx.JmxClient
import com.google.common.util.concurrent.ThreadFactoryBuilder
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.net.URI
import java.util.concurrent.Executors

/**
 * @param extraMeasurementSources source of results/diagnostics of: reverse proxy, Crowd, LDAP, DVCS, DB, Jira plugins
 * or anything that can be integrated with Jira as part of web application provisioning
 */
class Jira private constructor(
    private val nodes: List<StartedNode>,
    val jiraHome: RemoteLocation,
    val database: RemoteLocation,
    val address: URI,
    val jmxClients: List<JmxClient>,
    private val extraMeasurementSources: List<MeasurementSource>
) : MeasurementSource {
    private val logger: Logger = LogManager.getLogger(this::class.java)

    override fun gatherResults() {
        val firstNode = nodes.firstOrNull()
        val measurementSources = extraMeasurementSources + nodes + listOfNotNull(firstNode?.let { AnalyticsLogsSource(it) })
        if (measurementSources.isEmpty()) {
            logger.warn("No result sources known to Jira, can't download anything")
            return
        }
        val executor = Executors.newFixedThreadPool(
            measurementSources.size.coerceAtMost(4),
            ThreadFactoryBuilder()
                .setNameFormat("results-gathering-thread-%d")
                .build()
        )
        FailSafeRunnable(
            measurementSources.map { executor.submitWithLogContext("gather $it") { it.gatherResults() } }
                .map { Runnable { it.get() } }
        ).run()

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
        private var extraMeasurementSources: List<MeasurementSource> = emptyList()

        fun jmxClients(jmxClients: List<JmxClient>) = apply { this.jmxClients = jmxClients }
        fun extraMeasurementSources(extraMeasurementSources: List<MeasurementSource>) = apply { this.extraMeasurementSources = extraMeasurementSources }

        fun build() = Jira(
            nodes = nodes,
            jiraHome = jiraHome,
            database = database,
            address = address,
            jmxClients = jmxClients,
            extraMeasurementSources = extraMeasurementSources
        )
    }

    private class AnalyticsLogsSource(
        private val node: StartedNode
    ) : MeasurementSource {
        override fun gatherResults() {
            node.gatherAnalyticLogs()
        }
    }
}
