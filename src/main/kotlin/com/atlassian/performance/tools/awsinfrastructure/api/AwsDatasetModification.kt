package com.atlassian.performance.tools.awsinfrastructure.api

import com.atlassian.performance.tools.aws.api.Aws
import com.atlassian.performance.tools.aws.api.Investment
import com.atlassian.performance.tools.aws.api.Resource
import com.atlassian.performance.tools.awsinfrastructure.api.hardware.C5NineExtraLargeEphemeral
import com.atlassian.performance.tools.awsinfrastructure.api.jira.Jira
import com.atlassian.performance.tools.awsinfrastructure.api.jira.StandaloneFormula
import com.atlassian.performance.tools.awsinfrastructure.api.virtualusers.AbsentVirtualUsersFormula
import com.atlassian.performance.tools.infrastructure.api.dataset.Dataset
import com.atlassian.performance.tools.infrastructure.api.distribution.PublicJiraSoftwareDistribution
import com.atlassian.performance.tools.ssh.api.Ssh
import com.atlassian.performance.tools.workspace.api.RootWorkspace
import com.atlassian.performance.tools.workspace.api.TestWorkspace
import org.apache.logging.log4j.LogManager.getLogger
import java.time.Duration
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.function.Consumer

class AwsDatasetModification private constructor(
    private val aws: Aws,
    private val dataset: Dataset,
    private val workspace: TestWorkspace,
    private val newDatasetName: String,
    private val formula: InfrastructureFormula<*>,
    private val onlineTransformation: Consumer<Infrastructure<*>>
) {

    fun modify(): Dataset {
        val provisionedInfrastructure = provision()
        val infrastructure = provisionedInfrastructure.infrastructure
        val resource = provisionedInfrastructure.resource
        try {
            apply(infrastructure)
            cleanUp(infrastructure)
            return persist(infrastructure.jira)
        } finally {
            release(resource)
        }
    }

    private fun provision(): ProvisionedInfrastructure<*> {
        logger.info("Provisioning the ${dataset.label} dataset ...")
        val infrastructure = formula.provision(workspace.directory)
        logger.info("Provisioned successfully")
        return infrastructure
    }

    private fun apply(
        infrastructure: Infrastructure<*>
    ) {
        logger.info("Modifying the dataset ...")
        onlineTransformation.accept(infrastructure)
        logger.info("Dataset modified")
        logger.info("To avoid leaking license, do not make this modified dataset publicly available.")
    }

    private fun persist(
        jira: Jira
    ): Dataset {
        logger.info("Persisting the $newDatasetName dataset ...")
        val source = CustomDatasetSource(
            jiraHome = jira.jiraHome,
            database = jira.database ?: throw Exception("The database should have been provisioned")
        )
        val storedDataset = source.store(
            aws.customDatasetStorage(newDatasetName).location
        )
        logger.info("Dataset $newDatasetName persisted")
        return storedDataset
    }

    private fun release(resource: Resource) {
        logger.info("Releasing AWS resources ...")
        resource.release().get(2, TimeUnit.MINUTES)
        logger.info("AWS resources released")
    }

    private fun cleanUp(infrastructure: Infrastructure<*>) {
        val jiraHome = infrastructure.jira.jiraHome
        val jiraHomeDir = jiraHome.location
        val dirsToPurge = listOf(
            "$jiraHomeDir/analytics-logs/*",
            "$jiraHomeDir/log/*",
            "$jiraHomeDir/plugins/*",
            "$jiraHomeDir/export/*",
            "$jiraHomeDir/import/*"
        )
        Ssh(jiraHome.host, connectivityPatience = 4)
            .newConnection()
            .use { ssh ->
                dirsToPurge.forEach {
                    ssh.execute("rm -rf $it")
                }
            }
    }

    class Builder(
        private val aws: Aws,
        private val dataset: Dataset
    ) {
        private var onlineTransformation = Consumer<Infrastructure<*>> { }
        private var workspace: TestWorkspace = RootWorkspace().currentTask.isolateTest(javaClass.simpleName)
        private var newDatasetName: String = "dataset-${UUID.randomUUID()}"
        private var formula: InfrastructureFormula<*> = InfrastructureFormula(
            investment = Investment(
                useCase = "Generic purpose dataset modification",
                lifespan = Duration.ofMinutes(50)
            ),
            jiraFormula = StandaloneFormula.Builder(
                database = dataset.database,
                jiraHomeSource = dataset.jiraHomeSource,
                productDistribution = PublicJiraSoftwareDistribution("7.2.0")
            ).computer(C5NineExtraLargeEphemeral()).build(),
            virtualUsersFormula = AbsentVirtualUsersFormula(),
            aws = aws
        )

        fun onlineTransformation(onlineTransformation: Consumer<Infrastructure<*>>) = apply { this.onlineTransformation = onlineTransformation }
        fun workspace(workspace: TestWorkspace) = apply { this.workspace = workspace }
        fun newDatasetName(newDatasetName: String) = apply { this.newDatasetName = newDatasetName }
        fun formula(formula: InfrastructureFormula<*>) = apply { this.formula = formula }

        fun build() = AwsDatasetModification(
            aws = aws,
            dataset = dataset,
            workspace = workspace,
            newDatasetName = newDatasetName,
            formula = formula,
            onlineTransformation = onlineTransformation
        )
    }

    private companion object {
        private val logger = getLogger(AwsDatasetModification::class.java)
    }
}
