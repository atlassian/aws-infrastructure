package com.atlassian.performance.tools.awsinfrastructure.api.jira

import com.atlassian.performance.tools.aws.api.Aws
import com.atlassian.performance.tools.aws.api.Investment
import com.atlassian.performance.tools.aws.api.SshKey
import com.atlassian.performance.tools.aws.api.Storage
import java.util.concurrent.Future

class ProvisionedJiraFormula(
    private val provisionedJira: ProvisionedJira
) : JiraFormula {
    override fun provision(
        investment: Investment,
        pluginsTransport: Storage,
        resultsTransport: Storage,
        key: Future<SshKey>,
        roleProfile: String,
        aws: Aws
    ): ProvisionedJira = provisionedJira
}