package com.atlassian.performance.tools.awsinfrastructure

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain
import com.amazonaws.regions.Regions
import com.atlassian.performance.tools.aws.api.Aws
import com.atlassian.performance.tools.workspace.api.RootWorkspace
import org.apache.logging.log4j.core.config.ConfigurationFactory

object IntegrationTestRuntime {
    val taskWorkspace = RootWorkspace().currentTask
    val aws: Aws

    init {
        ConfigurationFactory.setConfigurationFactory(LogConfigurationFactory(taskWorkspace))
        aws = Aws(
            region = Regions.EU_WEST_1,
            credentialsProvider = DefaultAWSCredentialsProviderChain()
        )
    }
}
