package com.atlassian.performance.tools.awsinfrastructure.loadbalancer

import com.atlassian.performance.tools.infrastructure.api.Sed
import com.atlassian.performance.tools.infrastructure.api.jira.flow.JiraNodeFlow
import com.atlassian.performance.tools.infrastructure.api.jira.flow.install.InstalledJira
import com.atlassian.performance.tools.infrastructure.api.jira.flow.install.InstalledJiraHook
import com.atlassian.performance.tools.ssh.api.SshConnection
import java.net.URI

class ApacheProxyFix(
    private val loadBalancer: URI
) : InstalledJiraHook {

    override fun hook(ssh: SshConnection, jira: InstalledJira, flow: JiraNodeFlow) {
        Sed().replace(
            ssh,
            "bindOnInit=\"false\"",
            "bindOnInit=\"false\" scheme=\"http\" proxyName=\"${loadBalancer.host}\" proxyPort=\"80\"",
            "${jira.installation}/conf/server.xml"
        )
    }
}
