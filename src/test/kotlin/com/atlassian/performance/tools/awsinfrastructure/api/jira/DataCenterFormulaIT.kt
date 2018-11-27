package com.atlassian.performance.tools.awsinfrastructure.api.jira

import com.atlassian.performance.tools.aws.api.Investment
import com.atlassian.performance.tools.aws.api.SshKeyFormula
import com.atlassian.performance.tools.awsinfrastructure.IntegrationTestRuntime.aws
import com.atlassian.performance.tools.awsinfrastructure.IntegrationTestRuntime.taskWorkspace
import com.atlassian.performance.tools.awsinfrastructure.api.DatasetCatalogue
import com.atlassian.performance.tools.awsinfrastructure.api.hardware.C5NineExtraLargeEphemeral
import com.atlassian.performance.tools.awsinfrastructure.api.loadbalancer.ElasticLoadBalancerFormula
import com.atlassian.performance.tools.awsinfrastructure.api.storage.JiraSoftwareStorage
import com.atlassian.performance.tools.infrastructure.api.app.Apps
import com.atlassian.performance.tools.infrastructure.api.app.NoApp
import com.atlassian.performance.tools.infrastructure.api.jira.JiraJvmArgs
import com.atlassian.performance.tools.infrastructure.api.jira.JiraLaunchTimeouts
import com.atlassian.performance.tools.infrastructure.api.jira.JiraNodeConfig
import org.junit.Test
import java.time.Duration
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class DataCenterFormulaIT {
    private val workspace = taskWorkspace.isolateTest(javaClass.simpleName)
    private val dataset = DatasetCatalogue().largeJira()

    @Test
    fun shouldProvisionDataCenter() {
        val nonce = UUID.randomUUID().toString()
        val lifespan = Duration.ofMinutes(30)
        val keyFormula = SshKeyFormula(
            ec2 = aws.ec2,
            workingDirectory = workspace.directory,
            lifespan = lifespan,
            prefix = nonce
        )
        val dcFormula = DataCenterFormula(
            apps = Apps(listOf(NoApp())),
            application = JiraSoftwareStorage("7.2.0"),
            database = dataset.database,
            jiraHomeSource = dataset.jiraHomeSource,
            configs = JiraNodeConfig(
                name = "jira-node",
                jvmArgs = JiraJvmArgs(),
                launchTimeouts = JiraLaunchTimeouts(
                    offlineTimeout = Duration.ofMinutes(8),
                    initTimeout = Duration.ofMinutes(4),
                    upgradeTimeout = Duration.ofMinutes(8),
                    unresponsivenessTimeout = Duration.ofMinutes(4)
                )
            ).clone(2),
            loadBalancerFormula = ElasticLoadBalancerFormula(),
            computer = C5NineExtraLargeEphemeral()
        )

        val (_, resource) = dcFormula.provision(
            investment = Investment(
                useCase = "Test Data Center provisioning",
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
