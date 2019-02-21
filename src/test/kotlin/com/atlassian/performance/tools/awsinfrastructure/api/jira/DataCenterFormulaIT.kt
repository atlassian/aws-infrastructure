package com.atlassian.performance.tools.awsinfrastructure.api.jira

import com.atlassian.performance.tools.aws.api.Investment
import com.atlassian.performance.tools.aws.api.SshKeyFormula
import com.atlassian.performance.tools.awsinfrastructure.IntegrationTestRuntime
import com.atlassian.performance.tools.awsinfrastructure.api.DatasetCatalogue
import com.atlassian.performance.tools.awsinfrastructure.api.hardware.C5NineExtraLargeEphemeral
import com.atlassian.performance.tools.concurrency.api.submitWithLogContext
import com.atlassian.performance.tools.infrastructure.api.dataset.Dataset
import com.atlassian.performance.tools.infrastructure.api.distribution.PublicJiraSoftwareDistribution
import com.atlassian.performance.tools.infrastructure.api.jira.JiraNodeConfig
import com.atlassian.performance.tools.infrastructure.api.jvm.jmx.EnabledRemoteJmx
import com.atlassian.performance.tools.workspace.api.TaskWorkspace
import com.atlassian.performance.tools.workspace.api.TestWorkspace
import com.google.common.util.concurrent.ThreadFactoryBuilder
import org.apache.logging.log4j.CloseableThreadContext
import org.assertj.core.api.Assertions.assertThat
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
    private val jiraVersionEight = "8.0.0"
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
            DataCenterJmxProvisioningTest(
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
        override val jiraVersion: String,
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
            val dcFormula = DataCenterFormula.Builder(
                productDistribution = PublicJiraSoftwareDistribution(jiraVersion),
                jiraHomeSource = dataset.jiraHomeSource,
                database = dataset.database
            ).computer(C5NineExtraLargeEphemeral())
                .build()

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

    private inner class DataCenterJmxProvisioningTest(
        override val jiraVersion: String,
        private val dataset: Dataset
    ) : GroupableTest("Data Center with JMX provisioning - Jira version $jiraVersion") {
        override fun run(workspace: TestWorkspace) {
            val nonce = UUID.randomUUID().toString()
            val lifespan = Duration.ofMinutes(30)
            val keyFormula = SshKeyFormula(
                ec2 = aws.ec2,
                workingDirectory = workspace.directory,
                lifespan = lifespan,
                prefix = nonce
            )
            val config = JiraNodeConfig.Builder()
                .remoteJmx(EnabledRemoteJmx())
                .build()
            val dcFormula = DataCenterFormula.Builder(
                productDistribution = PublicJiraSoftwareDistribution(jiraVersion),
                jiraHomeSource = dataset.jiraHomeSource,
                database = dataset.database
            ).computer(C5NineExtraLargeEphemeral())
                .configs(
                    (1..2).map {
                        JiraNodeConfig.Builder(config)
                            .name("${config.name}-$it")
                            .build()
                    }
                )
                .build()

            val provisionedJira = dcFormula.provision(
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
            val resource = provisionedJira.resource

            provisionedJira.jira.jmxClients.forEach { client ->
                client.execute { connector -> assertThat(connector.mBeanServerConnection.mBeanCount).isGreaterThan(0) }
            }

            resource.release().get(1, TimeUnit.MINUTES)
        }
    }

    private abstract class GroupableTest(
        protected val feature: String
    ) {
        abstract val jiraVersion: String

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