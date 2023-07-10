package com.atlassian.performance.tools.awsinfrastructure.api

import com.atlassian.performance.tools.aws.api.Investment
import com.atlassian.performance.tools.aws.api.SshKeyFormula
import com.atlassian.performance.tools.awsinfrastructure.IntegrationTestRuntime
import com.atlassian.performance.tools.awsinfrastructure.IntegrationTestRuntime.aws
import com.atlassian.performance.tools.awsinfrastructure.api.jira.ProvisionedJira
import com.atlassian.performance.tools.awsinfrastructure.api.jira.StandaloneFormula
import com.atlassian.performance.tools.infrastructure.api.distribution.PublicJiraSoftwareDistribution
import com.atlassian.performance.tools.ssh.api.Ssh
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.time.Duration
import java.util.*
import java.util.concurrent.CompletableFuture

class CustomDatasetSourceIT {
    private val workspace = IntegrationTestRuntime.taskWorkspace.isolateTest(javaClass.simpleName)
    private val nonce = UUID.randomUUID().toString()

    @Test
    fun shouldStopDatabaseDockerContainer() {
        //given
        val provisionedJira = provisionStandaloneJira()
        val datasetSource = provisionedJira.jira.toDatasetSource().build()

        //when
        datasetSource.store(aws.customDatasetStorage(nonce).location)

        //then
        val databaseSsh = Ssh(datasetSource.database.host)
        val containerList = databaseSsh.newConnection().use { ssh ->
            ssh.execute("sudo docker ps -q")
        }
        assertThat(containerList.output).isEmpty()
    }

    private fun provisionStandaloneJira(): ProvisionedJira {
        val sourceDataset = DatasetCatalogue().smallJiraNine()
        val lifespan = Duration.ofHours(1)
        val keyFormula = SshKeyFormula(
            ec2 = aws.ec2,
            workingDirectory = workspace.directory,
            lifespan = lifespan,
            prefix = nonce
        )
        return StandaloneFormula.Builder(
            database = sourceDataset.database,
            jiraHomeSource = sourceDataset.jiraHomeSource,
            productDistribution = PublicJiraSoftwareDistribution("9.9.0")
        )
            .build()
            .provision(
                investment = Investment(
                    useCase = "Test CustomDatasetSource",
                    lifespan = lifespan
                ),
                pluginsTransport = aws.jiraStorage(nonce),
                resultsTransport = aws.resultsStorage(nonce),
                key = CompletableFuture.completedFuture(keyFormula.provision()),
                roleProfile = aws.shortTermStorageAccess(),
                aws = aws
            )
    }
}
