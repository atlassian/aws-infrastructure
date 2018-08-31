package com.atlassian.performance.tools.awsinfrastructure.api.virtualusers

import com.atlassian.performance.tools.aws.api.*
import com.atlassian.performance.tools.concurrency.api.submitWithLogContext
import com.atlassian.performance.tools.infrastructure.api.virtualusers.MulticastVirtualUsers
import com.atlassian.performance.tools.infrastructure.api.virtualusers.ResultsTransport
import com.atlassian.performance.tools.infrastructure.api.virtualusers.SshVirtualUsers
import com.atlassian.performance.tools.virtualusers.api.VirtualUserLoad
import com.google.common.util.concurrent.ThreadFactoryBuilder
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.Future

class MulticastVirtualUsersFormula(
    private val shadowJar: File,
    private val virtualUsersLoad: VirtualUserLoad
) : VirtualUsersFormula<MulticastVirtualUsers<SshVirtualUsers>> {

    override fun provision(
        investment: Investment,
        shadowJarTransport: Storage,
        resultsTransport: ResultsTransport,
        key: Future<SshKey>,
        roleProfile: String,
        aws: Aws
    ): ProvisionedVirtualUsers<MulticastVirtualUsers<SshVirtualUsers>> {
        val virtualUsers = virtualUsersLoad.virtualUsers
        val executor = Executors.newFixedThreadPool(
            virtualUsers,
            ThreadFactoryBuilder()
                .setNameFormat("multicast-virtual-users-provisioning-thread-%d")
                .build()
        )

        val provisionedVirtualUsers = (1..virtualUsers)
            .map { nodeOrder ->
                executor.submitWithLogContext("provision virtual users $nodeOrder") {
                    StackVirtualUsersFormula(
                        nodeOrder = nodeOrder,
                        shadowJar = shadowJar
                    ).provision(
                        investment = investment.copy(reuseKey = { investment.reuseKey() + nodeOrder }),
                        shadowJarTransport = shadowJarTransport,
                        resultsTransport = resultsTransport,
                        key = key,
                        roleProfile = roleProfile,
                        aws = aws
                    )
                }
            }
            .map { it.get() }

        executor.shutdownNow()

        return ProvisionedVirtualUsers(
            virtualUsers = MulticastVirtualUsers(provisionedVirtualUsers.map { it.virtualUsers }),
            resource = CompositeResource(provisionedVirtualUsers.map { it.resource })
        )
    }
}
