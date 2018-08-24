package com.atlassian.performance.tools.awsinfrastructure

import com.atlassian.performance.tools.aws.SshKey
import com.atlassian.performance.tools.aws.Storage
import com.atlassian.performance.tools.awsinfrastructure.jira.Jira
import com.atlassian.performance.tools.concurrency.submitWithLogContext
import com.atlassian.performance.tools.infrastructure.api.MeasurementSource
import com.atlassian.performance.tools.infrastructure.api.virtualusers.LoadProfile
import com.atlassian.performance.tools.infrastructure.api.virtualusers.VirtualUsers
import com.atlassian.performance.tools.jiraactions.scenario.Scenario
import com.atlassian.performance.tools.jvmtasks.TaskTimer.time
import com.google.common.util.concurrent.ThreadFactoryBuilder
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.nio.file.Path
import java.util.concurrent.Executors

data class Infrastructure<out T : VirtualUsers>(
    val virtualUsers: T,
    val jira: Jira,
    private val resultsTransport: Storage,
    val sshKey: SshKey
) {
    private val logger: Logger = LogManager.getLogger(this::class.java)

    fun applyLoad(
        loadProfile: LoadProfile,
        scenarioClass: Class<out Scenario>?
    ) {
        time("applying load") { virtualUsers.applyLoad(jira.address, loadProfile, scenarioClass) }
    }

    fun downloadResults(
        target: Path
    ): Path {
        logger.info("Downloading results...")
        val resultSources: List<MeasurementSource> = listOf(virtualUsers, jira)
        val executor = Executors.newFixedThreadPool(
            resultSources.size.butNotMoreThan(4),
            ThreadFactoryBuilder()
                .setNameFormat("gather-results-thread-%d")
                .build()
        )
        resultSources
            .map { executor.submitWithLogContext("results") { it.gatherResults() } }
            .forEach {
                try {
                    it.get()
                } catch (e: Exception) {
                    logger.error("Failed to gather results. Proceeding...", e)
                }
            }
        executor.shutdownNow()
        logger.debug("Results are gathered")
        val results = resultsTransport.download(target)
        logger.info("Results are downloaded")
        return results
    }
}

private fun Int.butNotMoreThan(
    max: Int
) = Math.min(this, max)