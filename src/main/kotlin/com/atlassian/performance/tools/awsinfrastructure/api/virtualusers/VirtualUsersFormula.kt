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

data class ProvisionedVirtualUsers<out T : VirtualUsers>(
    val virtualUsers: T,
    val resource: Resource
)