package com.atlassian.performance.tools.awsinfrastructure.api.jira

import com.atlassian.performance.tools.aws.api.Investment
import com.atlassian.performance.tools.aws.api.SshKeyFormula
import com.atlassian.performance.tools.awsinfrastructure.IntegrationTestRuntime
import com.atlassian.performance.tools.awsinfrastructure.api.DatasetCatalogue
import com.atlassian.performance.tools.infrastructure.api.distribution.PublicJiraServiceDeskDistribution
import com.atlassian.performance.tools.infrastructure.api.jira.flow.JiraNodeFlow
import com.atlassian.performance.tools.infrastructure.api.jira.flow.install.DefaultJiraInstallation
import com.atlassian.performance.tools.infrastructure.api.jira.flow.install.HookedJiraInstallation
import com.atlassian.performance.tools.infrastructure.api.jira.flow.start.HookedJiraStart
import com.atlassian.performance.tools.infrastructure.api.jira.flow.start.JiraLaunchScript
import com.atlassian.performance.tools.infrastructure.api.jvm.OracleJDK
import org.junit.Test
import java.time.Duration
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class FlowServerFormulaIT {

    private val workspace = IntegrationTestRuntime.taskWorkspace.isolateTest(javaClass.simpleName)
    private val dataset = DatasetCatalogue().largeJiraSeven()

    @Test
    fun shouldProvisionServer() {
        val nonce = UUID.randomUUID().toString()
        val lifespan = Duration.ofMinutes(30)
        val keyFormula = SshKeyFormula(
            ec2 = IntegrationTestRuntime.aws.ec2,
            workingDirectory = workspace.directory,
            lifespan = lifespan,
            prefix = nonce
        )
        val serverFormula = FlowServerFormula.Builder()
            .installation(
                HookedJiraInstallation(
                    DefaultJiraInstallation(
                        jiraHomeSource = dataset.jiraHomeSource,
                        productDistribution = PublicJiraServiceDeskDistribution("3.9.8"),
                        jdk = OracleJDK()
                    )
                )
            )
            .flow(
                JiraNodeFlow().apply {
                    hookPostInstall()
                }
            )
            .build()

        val resource = serverFormula.provision(
            investment = Investment(
                useCase = "Test Server provisioning flow API",
                lifespan = lifespan
            ),
            pluginsTransport = IntegrationTestRuntime.aws.jiraStorage(nonce),
            resultsTransport = IntegrationTestRuntime.aws.resultsStorage(nonce),
            key = CompletableFuture.completedFuture(keyFormula.provision()),
            roleProfile = IntegrationTestRuntime.aws.shortTermStorageAccess(),
            aws = IntegrationTestRuntime.aws
        ).resource

        resource.release().get(1, TimeUnit.MINUTES)
    }
}