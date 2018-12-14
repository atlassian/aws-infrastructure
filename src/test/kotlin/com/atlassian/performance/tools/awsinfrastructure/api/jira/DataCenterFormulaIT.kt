package com.atlassian.performance.tools.awsinfrastructure.api.jira

import com.atlassian.performance.tools.aws.api.Investment
import com.atlassian.performance.tools.aws.api.SshKeyFormula
import com.atlassian.performance.tools.awsinfrastructure.IntegrationTestRuntime
import com.atlassian.performance.tools.awsinfrastructure.api.DatasetCatalogue
import com.atlassian.performance.tools.awsinfrastructure.api.hardware.C5NineExtraLargeEphemeral
import com.atlassian.performance.tools.awsinfrastructure.api.loadbalancer.ElasticLoadBalancerFormula
import com.atlassian.performance.tools.awsinfrastructure.api.storage.JiraSoftwareStorage
import com.atlassian.performance.tools.concurrency.api.submitWithLogContext
import com.atlassian.performance.tools.infrastructure.api.app.Apps
import com.atlassian.performance.tools.infrastructure.api.app.NoApp
import com.atlassian.performance.tools.infrastructure.api.dataset.Dataset
import com.atlassian.performance.tools.infrastructure.api.jira.JiraJvmArgs
import com.atlassian.performance.tools.infrastructure.api.jira.JiraLaunchTimeouts
import com.atlassian.performance.tools.infrastructure.api.jira.JiraNodeConfig
import com.atlassian.performance.tools.workspace.api.TaskWorkspace
import com.atlassian.performance.tools.workspace.api.TestWorkspace
import com.google.common.util.concurrent.ThreadFactoryBuilder
import org.apache.logging.log4j.CloseableThreadContext
import org.junit.Test
import java.time.Duration
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class DataCenterFormulaIT {
    private val workspace = IntegrationTestRuntime.taskWorkspace
    private val aws = IntegrationTestRuntime.aws
    private val jiraVersionSeven = "7.2.0"
    private val jiraVersionEight = "8.0.0-m0030"
    private val datasetSeven = DatasetCatalogue().largeJiraSeven()
    private val datasetEight = DatasetCatalogue().largeJiraEight()

    @Test
    fun shouldProvisionDataCenter() {
        val executor = Executors.newFixedThreadPool(
            2,
            ThreadFactoryBuilder()
                .setNameFormat("DCFIT-test-thread-%d")
                .build()
        )
        listOf(
            DataCenterProvisioningTest(
                jiraVersion = jiraVersionSeven,
                dataset = datasetSeven
            ),
            DataCenterProvisioningTest(
                jiraVersion = jiraVersionEight,
                dataset = datasetEight
            )
        )
            .map { test ->
                executor.submitWithLogContext(test.jiraVersion) {
                    test.run(
                        group = "DCFIT-test",
                        workspace = workspace
                    )
                }
            }
            .forEach { it.get() }
        executor.shutdownNow()
    }

    private inner class DataCenterProvisioningTest(
        val jiraVersion: String,
        private val dataset: Dataset
    ) : GroupableTest("Data Center provisioning - Jira version $jiraVersion") {
        override fun run(workspace: TestWorkspace) {
            val nonce = UUID.randomUUID().toString()
            val lifespan = Duration.ofMinutes(30)
            val keyFormula = SshKeyFormula(
                ec2 = aws.ec2,
                workingDirectory = workspace.directory,
                lifespan = lifespan,
                prefix = nonce
            )
            val dcFormula = DataCenterFormula(
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
                apps = Apps(listOf(NoApp())),
                application = JiraSoftwareStorage(jiraVersion),
                jiraHomeSource = dataset.jiraHomeSource,
                database = dataset.database,
                computer = C5NineExtraLargeEphemeral()
            )

            val resource = dcFormula.provision(
                investment = Investment(
                    useCase = "Test Data Center provisioning",
                    lifespan = lifespan
                ),
                pluginsTransport = aws.jiraStorage(nonce),
                resultsTransport = aws.resultsStorage(nonce),
                key = CompletableFuture.completedFuture(keyFormula.provision()),
                roleProfile = aws.shortTermStorageAccess(),
                aws = aws
            ).resource

            resource.release().get(1, TimeUnit.MINUTES)
        }
    }

    private abstract class GroupableTest(
        protected val feature: String
    ) {
        fun run(
            group: String,
            workspace: TaskWorkspace
        ) {
            CloseableThreadContext.put("test", "$group : $feature").use {
                run(workspace.isolateTest("$group - $feature"))
            }
        }

        abstract fun run(workspace: TestWorkspace)
    }
}