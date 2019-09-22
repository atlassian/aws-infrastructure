package com.atlassian.performance.tools.awsinfrastructure

import com.amazonaws.regions.Regions
import com.atlassian.performance.tools.aws.api.Aws
import com.atlassian.performance.tools.workspace.api.RootWorkspace
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.core.config.ConfigurationFactory
import org.apache.logging.log4j.spi.LoggerContext
import java.time.Duration
import java.util.function.Predicate

object IntegrationTestRuntime {
    val taskWorkspace = RootWorkspace().currentTask
    val aws: Aws
    val logContext: LoggerContext

    init {
        ConfigurationFactory.setConfigurationFactory(LogConfigurationFactory(taskWorkspace))
        logContext = LogManager.getContext()
        aws = Aws.Builder(Regions.EU_WEST_1)
            .availabilityZoneFilter(Predicate { it.zoneName in listOf("eu-west-1a", "eu-west-1c") })
            .regionsWithHousekeeping(listOf(Regions.EU_WEST_1))
            .batchingCloudformationRefreshPeriod(Duration.ofSeconds(15))
            .build()
    }
}
