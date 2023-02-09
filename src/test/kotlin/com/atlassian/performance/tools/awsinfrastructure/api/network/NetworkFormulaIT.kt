package com.atlassian.performance.tools.awsinfrastructure.api.network

import com.atlassian.performance.tools.aws.api.Investment
import com.atlassian.performance.tools.awsinfrastructure.IntegrationTestRuntime.aws
import org.junit.Test
import java.time.Duration
import java.util.concurrent.TimeUnit.MINUTES

class NetworkFormulaIT {

    @Test
    fun shouldReleaseResources() {
        aws.cleanLeftovers()
        val investment = Investment(
            useCase = "test NetworkFormula",
            lifespan = Duration.ofMinutes(10)
        )

        val network = NetworkFormula(investment, aws).provisionAsResource()
        network.resource.release().get(4, MINUTES)
    }
}