package com.atlassian.performance.tools.awsinfrastructure

import com.atlassian.performance.tools.aws.*
import com.atlassian.performance.tools.awsinfrastructure.jira.JiraFormula
import com.atlassian.performance.tools.awsinfrastructure.virtualusers.S3ResultsTransport
import com.atlassian.performance.tools.awsinfrastructure.virtualusers.VirtualUsersFormula
import com.atlassian.performance.tools.concurrency.submitWithLogContext
import com.atlassian.performance.tools.infrastructure.virtualusers.VirtualUsers
import com.google.common.util.concurrent.ThreadFactoryBuilder
import java.nio.file.Path
import java.util.concurrent.Executors

class InfrastructureFormula<out T : VirtualUsers>(
    private val investment: Investment,
    private val jiraFormula: JiraFormula,
    private val virtualUsersFormula: VirtualUsersFormula<T>,
    private val aws: Aws
) {
    fun provision(
        workingDirectory: Path
    ): ProvisionedInfrastructure<T> {
        val nonce = investment.reuseKey()

        val resultsStorage = aws.resultsStorage(nonce)
        val roleProfile = aws.shortTermStorageAccess()

        val executor = Executors.newFixedThreadPool(
            3,
            ThreadFactoryBuilder()
                .setNameFormat("provisioning-thread-%d")
                .build()
        )
        val keyProvisioning = executor.submitWithLogContext("provision key") {
            SshKeyFormula(
                ec2 = aws.ec2,
                workingDirectory = workingDirectory,
                prefix = nonce,
                lifespan = investment.lifespan
            ).provision()
        }

        val provisionJira = executor.submitWithLogContext("jira") {
            jiraFormula.provision(
                investment = investment,
                pluginsTransport = aws.jiraStorage(nonce),
                resultsTransport = resultsStorage,
                key = keyProvisioning,
                roleProfile = roleProfile,
                aws = aws
            )
        }

        val provisionVirtualUsers = executor.submitWithLogContext("virtual users") {
            virtualUsersFormula.provision(
                investment = investment,
                shadowJarTransport = aws.virtualUsersStorage(nonce),
                resultsTransport = S3ResultsTransport(
                    results = resultsStorage
                ),
                key = keyProvisioning,
                roleProfile = roleProfile,
                aws = aws
            )
        }

        val provisionedJira = provisionJira.get()
        val provisionedVirtualUsers = provisionVirtualUsers.get()
        val sshKey = keyProvisioning.get()

        executor.shutdownNow()

        return ProvisionedInfrastructure(
            infrastructure = Infrastructure(
                virtualUsers = provisionedVirtualUsers.virtualUsers,
                jira = provisionedJira.jira,
                resultsTransport = resultsStorage,
                sshKey = sshKey
            ),
            resource = CompositeResource(
                listOf(
                    provisionedJira.resource,
                    provisionedVirtualUsers.resource,
                    sshKey.remote
                )
            )
        )
    }
}

data class ProvisionedInfrastructure<out T : VirtualUsers>(
    val infrastructure: Infrastructure<T>,
    val resource: Resource
)