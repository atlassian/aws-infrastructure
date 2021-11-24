package com.atlassian.performance.tools.awsinfrastructure.api.jira

import com.atlassian.performance.tools.aws.api.*
import com.atlassian.performance.tools.awsinfrastructure.api.RemoteLocation
import com.atlassian.performance.tools.ssh.api.SshHost
import com.atlassian.performance.tools.ssh.api.auth.PasswordAuthentication
import java.net.URI
import java.util.concurrent.Future

class UriJiraFormula(
    private val jiraAddress: URI
) : JiraFormula {

    override fun provision(
        investment: Investment,
        pluginsTransport: Storage,
        resultsTransport: Storage,
        key: Future<SshKey>,
        roleProfile: String,
        aws: Aws
    ): ProvisionedJira = ProvisionedJira
        .Builder(
            Jira(
                nodes = emptyList(),
                jiraHome = RemoteLocation(
                    host = SshHost(
                        ipAddress = "unknown",
                        userName = "unknown",
                        authentication = PasswordAuthentication("unknown"),
                        port = -1
                    ),
                    location = "unknown"
                ),
                database = null,
                address = jiraAddress
            )
        )
        .build()
}
