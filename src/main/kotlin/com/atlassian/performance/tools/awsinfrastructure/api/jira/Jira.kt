package com.atlassian.performance.tools.awsinfrastructure.api.jira

import com.atlassian.performance.tools.awsinfrastructure.api.RemoteLocation
import com.atlassian.performance.tools.concurrency.submitWithLogContext
import com.atlassian.performance.tools.infrastructure.api.MeasurementSource
import com.atlassian.performance.tools.infrastructure.api.jvm.jmx.JmxClient
import com.atlassian.performance.tools.jvmtasks.TaskTimer.time
import com.google.common.util.concurrent.ThreadFactoryBuilder
import java.net.URI
import java.util.concurrent.Executors

class Jira(
    private val nodes: List<StartedNode>,
    val jiraHome: RemoteLocation,
    val database: RemoteLocation?,
    val address: URI,
    val jmxClients: List<JmxClient> = emptyList()
) : MeasurementSource {
    override fun gatherResults() {
        val executor = Executors.newFixedThreadPool(
            Math.min(nodes.size, 4),
            ThreadFactoryBuilder()
                .setNameFormat("results-gathering-thread-%d")
                .build()
        )
        nodes.map { executor.submitWithLogContext("gather $it") { it.gatherResults() } }
            .forEach { it.get() }
        time("gather analytics") { nodes.first().gatherAnalyticLogs() }
        executor.shutdownNow()
    }

    override fun toString() = "Jira(address=$address)"
}