package com.atlassian.performance.tools.awsinfrastructure.api.jira

import com.atlassian.performance.tools.aws.api.Investment
import com.atlassian.performance.tools.aws.api.SshKeyFormula
import com.atlassian.performance.tools.awsinfrastructure.IntegrationTestRuntime
import com.atlassian.performance.tools.awsinfrastructure.NetworkFormula
import com.atlassian.performance.tools.awsinfrastructure.api.DatasetCatalogue
import com.atlassian.performance.tools.awsinfrastructure.api.database.AwsSshMysql
import com.atlassian.performance.tools.awsinfrastructure.api.hardware.M5ExtraLargeEphemeral
import com.atlassian.performance.tools.awsinfrastructure.api.hardware.Volume
import com.atlassian.performance.tools.infrastructure.api.jira.flow.JiraNodeFlow
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
        val aws = IntegrationTestRuntime.aws
        val nonce = UUID.randomUUID().toString()
        val lifespan = Duration.ofMinutes(30)
        val sshKey = SshKeyFormula(
            ec2 = aws.ec2,
            workingDirectory = workspace.directory,
            lifespan = lifespan,
            prefix = nonce
        ).provision()
        val investment = Investment(
            useCase = "Test Server provisioning flow API",
            lifespan = lifespan
        )
        val network = NetworkFormula(investment, aws).provision()
        val mysqlHook = AwsSshMysql(
            dataset.database,
            aws,
            investment,
            M5ExtraLargeEphemeral(),
            Volume(100),
            network,
            sshKey,
            Duration.ofMinutes(4)
        )
        val serverFormula = FlowServerFormula.Builder()
            .node(
                JiraNodeProvisioning.Builder()
                    .flow(
                        JiraNodeFlow().apply { hookPreInstall(mysqlHook) }
                    )
                    .build()
            )
            .network(network)
            .build()

        val resource = serverFormula.provision(
            investment = investment,
            pluginsTransport = IntegrationTestRuntime.aws.jiraStorage(nonce),
            resultsTransport = IntegrationTestRuntime.aws.resultsStorage(nonce),
            key = CompletableFuture.completedFuture(sshKey),
            roleProfile = IntegrationTestRuntime.aws.shortTermStorageAccess(),
            aws = IntegrationTestRuntime.aws
        ).resource

        resource.release().get(1, TimeUnit.MINUTES)
    }
}