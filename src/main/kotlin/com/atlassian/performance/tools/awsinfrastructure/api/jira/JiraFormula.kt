package com.atlassian.performance.tools.awsinfrastructure.api.jira

import com.atlassian.performance.tools.aws.*
import java.util.concurrent.Future

interface JiraFormula {
    fun provision(
        investment: Investment,
        pluginsTransport: Storage,
        resultsTransport: Storage,
        key: Future<SshKey>,
        roleProfile: String,
        aws: Aws
    ): ProvisionedJira
}

data class ProvisionedJira(
    val jira: Jira,
    val resource: Resource
)