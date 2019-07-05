package com.atlassian.performance.tools.awsinfrastructure.jira.home

import com.atlassian.performance.tools.infrastructure.api.jira.SharedHome
import com.atlassian.performance.tools.infrastructure.api.jira.flow.JiraNodeFlow
import com.atlassian.performance.tools.infrastructure.api.jira.flow.install.InstalledJira
import com.atlassian.performance.tools.infrastructure.api.jira.flow.install.InstalledJiraHook
import com.atlassian.performance.tools.ssh.api.SshConnection

internal class SharedHomeHook(
    private val sharedHome: SharedHome
) : InstalledJiraHook {

    override fun run(ssh: SshConnection, jira: InstalledJira, flow: JiraNodeFlow) {
        sharedHome.mount(ssh)
        val mountedPath = "`realpath ${sharedHome.localSharedHome}`"
        ssh.execute("echo jira.shared.home = $mountedPath >> ${jira.home}/cluster.properties")
    }
}
