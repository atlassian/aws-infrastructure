package com.atlassian.performance.tools.awsinfrastructure.api.virtualusers

import com.atlassian.performance.tools.aws.api.*
import com.atlassian.performance.tools.infrastructure.api.virtualusers.ResultsTransport
import com.atlassian.performance.tools.infrastructure.api.virtualusers.VirtualUsers
import java.util.concurrent.Future

interface VirtualUsersFormula<out T : VirtualUsers> {
    fun provision(
        investment: Investment,
        shadowJarTransport: Storage,
        resultsTransport: ResultsTransport,
        key: Future<SshKey>,
        roleProfile: String,
        aws: Aws
    ): ProvisionedVirtualUsers<T>
}

interface VirtualUsersFormula2<out T : VirtualUsers> : VirtualUsersFormula<T> {
    fun provision(
        investment: Investment,
        shadowJarTransport: Storage,
        resultsTransport: ResultsTransport,
        key: Future<SshKey>,
        roleProfile: String,
        aws: Aws,
        nodeNumber: Int
    ): ProvisionedVirtualUsers<T>

    override fun provision(
        investment: Investment,
        shadowJarTransport: Storage,
        resultsTransport: ResultsTransport,
        key: Future<SshKey>,
        roleProfile: String,
        aws: Aws
    ): ProvisionedVirtualUsers<T> {
        return provision(
            investment,
            shadowJarTransport,
            resultsTransport,
            key,
            roleProfile,
            aws,
            1
        )
    }
}

class ProvisionedVirtualUsers<out T : VirtualUsers>(
    val virtualUsers: T,
    val resource: Resource
) {
    override fun toString(): String {
        return "ProvisionedVirtualUsers(virtualUsers=$virtualUsers, resource=$resource)"
    }
}