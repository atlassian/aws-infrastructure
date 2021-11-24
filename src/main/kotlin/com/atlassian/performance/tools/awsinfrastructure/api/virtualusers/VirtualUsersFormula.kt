package com.atlassian.performance.tools.awsinfrastructure.api.virtualusers

import com.atlassian.performance.tools.aws.api.Aws
import com.atlassian.performance.tools.aws.api.Investment
import com.atlassian.performance.tools.aws.api.SshKey
import com.atlassian.performance.tools.aws.api.Storage
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
