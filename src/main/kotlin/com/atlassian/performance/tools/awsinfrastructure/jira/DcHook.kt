package com.atlassian.performance.tools.awsinfrastructure.jira

import com.atlassian.performance.tools.infrastructure.api.jira.flow.JiraNodeFlow
import com.atlassian.performance.tools.infrastructure.api.jira.flow.install.InstalledJira
import com.atlassian.performance.tools.infrastructure.api.jira.flow.install.InstalledJiraHook
import com.atlassian.performance.tools.ssh.api.SshConnection

internal class DcHook(
    private val privateDcNodeIp: String
) : InstalledJiraHook {

    override fun hook(ssh: SshConnection, jira: InstalledJira, flow: JiraNodeFlow) {
        val clusterProperties = "${jira.home}/cluster.properties"
        ssh.execute("echo ehcache.listener.hostName = $privateDcNodeIp >> $clusterProperties")
        ssh.execute("echo ehcache.object.port = 40011 >> $clusterProperties")
        ssh.execute("echo jira.node.id = ${jira.server.name} >> $clusterProperties")
    }
}
