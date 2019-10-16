package com.atlassian.performance.tools.awsinfrastructure.jira.home

import com.atlassian.performance.tools.infrastructure.api.jira.SharedHome
import com.atlassian.performance.tools.infrastructure.api.jira.hook.PostInstallHooks
import com.atlassian.performance.tools.infrastructure.api.jira.hook.install.InstalledJira
import com.atlassian.performance.tools.infrastructure.api.jira.hook.install.PostInstallHook
import com.atlassian.performance.tools.ssh.api.SshConnection

internal class SharedHomeHook(
    private val sharedHome: SharedHome
) : PostInstallHook {

    override fun run(ssh: SshConnection, jira: InstalledJira, hooks: PostInstallHooks) {
        sharedHome.mount(ssh)
        val mountedPath = "`realpath ${sharedHome.localSharedHome}`"
        ssh.execute("echo jira.shared.home = $mountedPath >> ${jira.home}/cluster.properties")
    }
}
