package com.atlassian.performance.tools.awsinfrastructure.api

import com.atlassian.performance.tools.aws.api.Aws
import com.atlassian.performance.tools.aws.api.Investment
import com.atlassian.performance.tools.aws.api.Resource
import com.atlassian.performance.tools.awsinfrastructure.api.dataset.DatasetHost
import com.atlassian.performance.tools.awsinfrastructure.api.dataset.SshMysqlDatasetPublication
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
    private val host: DatasetHost,
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
        val infrastructure = host
            .host(dataset)
            .provision(workspace.directory)
        logger.info("Provisioned successfully")
        return infrastructure
    }

    private fun apply(
        infrastructure: Infrastructure<*>
    ) {
        logger.info("Modifying the dataset ...")
        onlineTransformation.accept(infrastructure)
        logger.info("Dataset modified")
        val publicationMechanism = SshMysqlDatasetPublication::class.java.canonicalName
        logger.info("It might contain a license. To publish this dataset, use the $publicationMechanism")
    }

    private fun persist(
        jira: Jira
    ): Dataset {
        logger.info("Persisting the $newDatasetName dataset ...")
        val source = CustomDatasetSource.Builder(
            jiraHome = jira.jiraHome,
            database = jira.database ?: throw Exception("The database should have been provisioned")
        ).build()
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
        internal var dataset: Dataset
    ) {
        private var onlineTransformation = Consumer<Infrastructure<*>> { }
        private var workspace: TestWorkspace = RootWorkspace().currentTask.isolateTest(javaClass.simpleName)
        private var newDatasetName: String = "dataset-${UUID.randomUUID()}"
        private var host: DatasetHost = DatasetHost {
            InfrastructureFormula(
                investment = Investment(
                    useCase = "Generic purpose dataset modification",
                    lifespan = Duration.ofMinutes(50)
                ),
                jiraFormula = StandaloneFormula.Builder(
                    database = it.database,
                    jiraHomeSource = it.jiraHomeSource,
                    productDistribution = PublicJiraSoftwareDistribution("7.2.0")
                ).computer(C5NineExtraLargeEphemeral()).build(),
                virtualUsersFormula = AbsentVirtualUsersFormula(),
                aws = aws
            )
        }

        /**
         * @since 2.12.0
         */
        fun dataset(dataset: Dataset) = apply { this.dataset = dataset }

        /**
         * @since 2.12.0
         */
        fun host(host: DatasetHost) = apply { this.host = host }

        fun onlineTransformation(onlineTransformation: Consumer<Infrastructure<*>>) = apply { this.onlineTransformation = onlineTransformation }
        fun workspace(workspace: TestWorkspace) = apply { this.workspace = workspace }
        fun newDatasetName(newDatasetName: String) = apply { this.newDatasetName = newDatasetName }

        @Deprecated("This ignores `dataset` building. Replace with `host` or `dataset`.")
        fun formula(formula: InfrastructureFormula<*>) = apply {
            this.host = DatasetHost { formula }
        }

        fun build() = AwsDatasetModification(
            aws = aws,
            dataset = dataset,
            workspace = workspace,
            newDatasetName = newDatasetName,
            host = host,
            onlineTransformation = onlineTransformation
        )
    }

    private companion object {
        private val logger = getLogger(AwsDatasetModification::class.java)
    }
}
