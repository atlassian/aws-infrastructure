package com.atlassian.performance.tools.awsinfrastructure.api.jira

import com.atlassian.performance.tools.aws.api.Aws
import com.atlassian.performance.tools.aws.api.Investment
import com.atlassian.performance.tools.aws.api.SshKey
import com.atlassian.performance.tools.aws.api.Storage
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
            Jira.Builder(
                nodes = emptyList(),
                jiraHome = dummyRemoteLocation(),
                database = dummyRemoteLocation(),
                address = jiraAddress
            )
                .build()
        )
        .build()

    private fun dummyRemoteLocation(): RemoteLocation {
        return RemoteLocation(
            host = SshHost(
                ipAddress = "unknown",
                userName = "unknown",
                authentication = PasswordAuthentication("unknown"),
                port = -1
            ),
            location = "unknown"
        )
    }
}
