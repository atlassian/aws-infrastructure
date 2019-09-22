package com.atlassian.performance.tools.awsinfrastructure.api.virtualusers

import com.atlassian.performance.tools.aws.api.*
import com.atlassian.performance.tools.concurrency.api.AbruptExecutorService
import com.atlassian.performance.tools.concurrency.api.submitWithLogContext
import com.atlassian.performance.tools.infrastructure.api.virtualusers.MulticastVirtualUsers
import com.atlassian.performance.tools.infrastructure.api.virtualusers.ResultsTransport
import com.atlassian.performance.tools.infrastructure.api.virtualusers.VirtualUsers
import java.util.concurrent.Executors
import java.util.concurrent.Future

class MultiVirtualUsersFormula<T : VirtualUsers>(
    private val base: VirtualUsersFormula2<T>,
    private val nodeCount: Int
) : VirtualUsersFormula<MulticastVirtualUsers<T>> {

    override fun provision(
        investment: Investment,
        shadowJarTransport: Storage,
        resultsTransport: ResultsTransport,
        key: Future<SshKey>,
        roleProfile: String,
        aws: Aws
    ): ProvisionedVirtualUsers<MulticastVirtualUsers<T>> {
        val provisionedVirtualUsers = AbruptExecutorService(
            Executors.newFixedThreadPool(nodeCount) { runnable ->
                Thread(runnable, "provision-multi-virtual-users-${runnable.hashCode()}")
            }
        ).use { executor ->
            (1..nodeCount)
                .map { nodeNumber ->
                    executor.submitWithLogContext("provision virtual users $nodeNumber") {
                        base.provision(
                            investment = investment.copy(reuseKey = { investment.reuseKey() + nodeNumber }),
                            shadowJarTransport = shadowJarTransport,
                            resultsTransport = resultsTransport,
                            key = key,
                            roleProfile = roleProfile,
                            aws = aws,
                            nodeNumber = nodeNumber
                        )
                    }
                }
                .map { it.get() }
        }
        return ProvisionedVirtualUsers(
            virtualUsers = MulticastVirtualUsers(provisionedVirtualUsers.map { it.virtualUsers }),
            resource = CompositeResource(provisionedVirtualUsers.map { it.resource })
        )
    }
}
