package com.atlassian.performance.tools.awsinfrastructure.api.jira

import com.atlassian.performance.tools.aws.api.Investment
import com.atlassian.performance.tools.aws.api.SshKeyFormula
import com.atlassian.performance.tools.awsinfrastructure.IntegrationTestRuntime.aws
import com.atlassian.performance.tools.awsinfrastructure.IntegrationTestRuntime.taskWorkspace
import com.atlassian.performance.tools.awsinfrastructure.api.DatasetCatalogue
import com.atlassian.performance.tools.awsinfrastructure.api.hardware.C5NineExtraLargeEphemeral
import com.atlassian.performance.tools.awsinfrastructure.api.storage.JiraServiceDeskStorage
import com.atlassian.performance.tools.infrastructure.api.app.Apps
import com.atlassian.performance.tools.infrastructure.api.app.NoApp
import com.atlassian.performance.tools.infrastructure.api.jira.JiraNodeConfig
import org.junit.Test
import java.time.Duration
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class StandaloneFormulaIT {

    private val workspace = taskWorkspace.isolateTest(javaClass.simpleName)
    private val dataset = DatasetCatalogue().largeJira()

    @Test
    fun shouldProvisionServer() {
        val nonce = UUID.randomUUID().toString()
        val lifespan = Duration.ofMinutes(30)
        val keyFormula = SshKeyFormula(
            ec2 = aws.ec2,
            workingDirectory = workspace.directory,
            lifespan = lifespan,
            prefix = nonce
        )
        val serverFormula = StandaloneFormula(
            apps = Apps(listOf(NoApp())),
            application = JiraServiceDeskStorage("3.9.8"),
            database = dataset.database,
            jiraHomeSource = dataset.jiraHomeSource,
            config = JiraNodeConfig(),
            computer = C5NineExtraLargeEphemeral()
        )

        val (_, resource) = serverFormula.provision(
            investment = Investment(
                useCase = "Test JSD Server provisioning",
                lifespan = lifespan
            ),
            pluginsTransport = aws.jiraStorage(nonce),
            resultsTransport = aws.resultsStorage(nonce),
            key = CompletableFuture.completedFuture(keyFormula.provision()),
            roleProfile = aws.shortTermStorageAccess(),
            aws = aws
        )

        resource.release().get(1, TimeUnit.MINUTES)
    }
}