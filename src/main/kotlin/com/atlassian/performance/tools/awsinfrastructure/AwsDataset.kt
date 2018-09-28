package com.atlassian.performance.tools.awsinfrastructure

import com.atlassian.performance.tools.aws.api.Aws
import com.atlassian.performance.tools.aws.api.Investment
import com.atlassian.performance.tools.aws.api.Resource
import com.atlassian.performance.tools.awsinfrastructure.api.CustomDatasetSource
import com.atlassian.performance.tools.awsinfrastructure.api.Infrastructure
import com.atlassian.performance.tools.awsinfrastructure.api.InfrastructureFormula
import com.atlassian.performance.tools.awsinfrastructure.api.ProvisionedInfrastructure
import com.atlassian.performance.tools.awsinfrastructure.api.jira.Jira
import com.atlassian.performance.tools.awsinfrastructure.api.jira.StandaloneFormula
import com.atlassian.performance.tools.awsinfrastructure.api.storage.JiraSoftwareStorage
import com.atlassian.performance.tools.awsinfrastructure.api.virtualusers.AbsentVirtualUsersFormula
import com.atlassian.performance.tools.infrastructure.api.app.Apps
import com.atlassian.performance.tools.infrastructure.api.app.NoApp
import com.atlassian.performance.tools.infrastructure.api.dataset.Dataset
import com.atlassian.performance.tools.infrastructure.api.jira.JiraNodeConfig
import com.atlassian.performance.tools.workspace.api.TaskWorkspace
import org.apache.logging.log4j.LogManager.getLogger
import java.time.Duration
import java.util.concurrent.TimeUnit

internal class AwsDataset(
    private val dataset: Dataset
) {

    fun modify(
        aws: Aws,
        workspace: TaskWorkspace,
        newDatasetName: String,
        modification: (Infrastructure<*>) -> Unit
    ): Dataset {
        val (infrastructure, resource) = provision(aws, workspace)
        modify(infrastructure, modification)
        val newDataset = persist(infrastructure.jira, aws, newDatasetName)
        release(resource)
        return newDataset
    }

    private fun provision(
        aws: Aws,
        workspace: TaskWorkspace
    ): ProvisionedInfrastructure<*> {
        logger.info("Provisioning the ${dataset.label} dataset ...")
        val formula = InfrastructureFormula(
            investment = Investment(
                useCase = "Clean backups from a dataset",
                lifespan = Duration.ofMinutes(50)
            ),
            jiraFormula = StandaloneFormula(
                apps = Apps(listOf(NoApp())),
                database = dataset.database,
                jiraHomeSource = dataset.jiraHomeSource,
                application = JiraSoftwareStorage("7.2.0"),
                config = JiraNodeConfig()
            ),
            virtualUsersFormula = AbsentVirtualUsersFormula(),
            aws = aws
        )
        val infrastructure = formula.provision(workspace.directory)
        logger.info("Provisioned successfully")
        return infrastructure
    }

    private fun modify(
        infrastructure: Infrastructure<*>,
        modification: (Infrastructure<*>) -> Unit
    ) {
        logger.info("Modifying the dataset ...")
        modification(infrastructure)
        logger.info("Dataset modified")
    }

    private fun persist(
        jira: Jira,
        aws: Aws,
        datasetName: String
    ): Dataset {
        logger.info("Persisting the $datasetName dataset ...")
        val source = CustomDatasetSource(
            jiraHome = jira.jiraHome,
            database = jira.database ?: throw Exception("The database should have been provisioned")
        )
        val storedDataset = source.store(
            aws.customDatasetStorage(datasetName).location
        )
        logger.info("Dataset $datasetName persisted")
        return storedDataset
    }

    private fun release(resource: Resource) {
        logger.info("Releasing AWS resources ...")
        resource.release().get(2, TimeUnit.MINUTES)
        logger.info("AWS resources released")
    }

    companion object {
        private val logger = getLogger(AwsDataset::class.java)
    }
}
