package com.atlassian.performance.tools.awsinfrastructure

import com.amazonaws.regions.Regions
import com.atlassian.performance.tools.aws.api.Aws
import com.atlassian.performance.tools.workspace.api.RootWorkspace
import org.apache.logging.log4j.core.config.ConfigurationFactory

object IntegrationTestRuntime {
    val taskWorkspace = RootWorkspace().currentTask
    val aws: Aws

    init {
        ConfigurationFactory.setConfigurationFactory(LogConfigurationFactory(taskWorkspace))
        aws = Aws.Builder(Regions.EU_CENTRAL_1)
            .regionsWithHousekeeping(listOf(Regions.EU_CENTRAL_1))
            .build()
    }
}
