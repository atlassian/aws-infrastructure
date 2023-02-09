package com.atlassian.performance.tools.awsinfrastructure.api

import com.atlassian.performance.tools.aws.api.*
import com.atlassian.performance.tools.awsinfrastructure.api.jira.DataCenterFormula
import com.atlassian.performance.tools.awsinfrastructure.api.jira.JiraFormula
import com.atlassian.performance.tools.awsinfrastructure.api.jira.JiraSoftwareDevDistribution
import com.atlassian.performance.tools.awsinfrastructure.api.jira.StandaloneFormula
import com.atlassian.performance.tools.awsinfrastructure.api.network.Network
import com.atlassian.performance.tools.awsinfrastructure.api.network.NetworkFormula
import com.atlassian.performance.tools.awsinfrastructure.api.virtualusers.Ec2VirtualUsersFormula
import com.atlassian.performance.tools.awsinfrastructure.api.virtualusers.MulticastVirtualUsersFormula
import com.atlassian.performance.tools.awsinfrastructure.api.virtualusers.StackVirtualUsersFormula
import com.atlassian.performance.tools.awsinfrastructure.api.virtualusers.VirtualUsersFormula
import com.atlassian.performance.tools.awsinfrastructure.virtualusers.S3ResultsTransport
import com.atlassian.performance.tools.concurrency.api.submitWithLogContext
import com.atlassian.performance.tools.infrastructure.api.virtualusers.VirtualUsers
import com.google.common.util.concurrent.ThreadFactoryBuilder
import org.apache.logging.log4j.CloseableThreadContext
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.Executors

/**
 * Groups [jiraFormula] and [virtualUsersFormula] into one unit, with a common lifecycle.
 *
 * Overrides some components to share the same network:
 * - [DataCenterFormula]
 * - [StandaloneFormula]
 * - [StackVirtualUsersFormula]
 * - [Ec2VirtualUsersFormula]
 * - [MulticastVirtualUsersFormula]
 */
class InfrastructureFormula<out T : VirtualUsers> private constructor(
    private val investment: Investment,
    private val jiraFormula: JiraFormula,
    private val virtualUsersFormula: VirtualUsersFormula<T>,
    private val aws: Aws,
    private val preProvisionedNetwork: Network?
) {
    private val logger: Logger = LogManager.getLogger(this::class.java)

    @Deprecated("Use InfrastructureFormula.Builder instead.")
    constructor(
        investment: Investment,
        jiraFormula: JiraFormula,
        virtualUsersFormula: VirtualUsersFormula<T>,
        aws: Aws
    ) : this(investment, jiraFormula, virtualUsersFormula, aws, null)

    fun provision(
        workingDirectory: Path
    ): ProvisionedInfrastructure<T> {
        logger.info("Provisioning infrastructure...")
        val nonce = investment.reuseKey()

        val resultsStorage = aws.resultsStorage(nonce)
        val roleProfile = aws.shortTermStorageAccess()

        val executor = Executors.newCachedThreadPool(
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

        val provisionedNetwork = CloseableThreadContext.push("network").use {
            NetworkFormula(investment, aws).reuseOrProvision(preProvisionedNetwork)
        }
        val network = provisionedNetwork.network

        val provisionJira = executor.submitWithLogContext("jira") {
            overrideJiraNetwork(network).provision(
                investment = investment,
                pluginsTransport = aws.jiraStorage(nonce),
                resultsTransport = resultsStorage,
                key = keyProvisioning,
                roleProfile = roleProfile,
                aws = aws
            )
        }

        val provisionVirtualUsers = executor.submitWithLogContext("virtual users") {
            overrideVuNetwork(network).provision(
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

        logger.info("Waiting until all Jira nodes are provisioned...")
        val provisionedJira = provisionJira.get()
        logger.info("All Jira nodes are available.")
        logger.info("Waiting until all virtual user nodes are provisioned...")
        val provisionedVirtualUsers = provisionVirtualUsers.get()
        val sshKey = keyProvisioning.get()

        provisionedVirtualUsers.accessRequester.requestAccess(provisionedJira.accessProvider)

        executor.shutdownNow()

        logger.info("All infrastructure is now available.")

        return ProvisionedInfrastructure
            .Builder(
                Infrastructure(
                    virtualUsers = provisionedVirtualUsers.virtualUsers,
                    jira = provisionedJira.jira,
                    resultsTransport = resultsStorage,
                    sshKey = sshKey
                )
            )
            .resource(
                CompositeResource(
                    listOf(
                        provisionedJira.resource,
                        provisionedVirtualUsers.resource,
                        provisionedNetwork.resource,
                        sshKey.remote
                    )
                )
            )
            .accessProvider(provisionedJira.accessProvider)
            .build()
    }

    private fun overrideJiraNetwork(
        network: Network
    ): JiraFormula = when (jiraFormula) {
        is DataCenterFormula -> DataCenterFormula.Builder(jiraFormula).network(network).build()
        is StandaloneFormula -> StandaloneFormula.Builder(jiraFormula).network(network).build()
        else -> jiraFormula
    }

    @Suppress("UNCHECKED_CAST")
    private fun overrideVuNetwork(
        network: Network
    ): VirtualUsersFormula<T> = when (virtualUsersFormula) {
        is StackVirtualUsersFormula -> StackVirtualUsersFormula.Builder(virtualUsersFormula).network(network).build()
        is Ec2VirtualUsersFormula -> Ec2VirtualUsersFormula.Builder(virtualUsersFormula).network(network).build()
        is MulticastVirtualUsersFormula -> MulticastVirtualUsersFormula.Builder(virtualUsersFormula).network(network).build()
        else -> virtualUsersFormula
    } as VirtualUsersFormula<T>

    class Builder<out T : VirtualUsers>(
        private val aws: Aws,         
        private val virtualUsersFormula: VirtualUsersFormula<T>
    ) {
        private var investment: Investment? = null
        private var jiraFormula: JiraFormula? = null
        private var network: Network? = null

        fun investment(investment: Investment) = apply { this.investment = investment }
        fun jiraFormula(jiraFormula: JiraFormula) = apply { this.jiraFormula = jiraFormula }
        fun network(network: Network) = apply { this.network = network }

        fun build(): InfrastructureFormula<T> = InfrastructureFormula(
            investment =  investment?: Investment("Default investment", Duration.ofMinutes(120)),
            jiraFormula =  jiraFormula?: defaultJiraFormula(),
            virtualUsersFormula =  virtualUsersFormula,
            aws = aws,
            preProvisionedNetwork = network
        )
        
        private fun defaultJiraFormula(): JiraFormula {
            val dataset = DatasetCatalogue().smallJiraSeven()
            return StandaloneFormula.Builder(
                productDistribution = JiraSoftwareDevDistribution("8.6.0"),
                jiraHomeSource = dataset.jiraHomeSource,
                database = dataset.database
            ).build()
        }
    }
}

