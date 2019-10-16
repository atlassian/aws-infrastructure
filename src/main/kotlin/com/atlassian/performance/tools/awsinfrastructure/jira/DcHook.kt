package com.atlassian.performance.tools.awsinfrastructure.jira

import com.atlassian.performance.tools.infrastructure.api.jira.hook.PostInstallHooks
import com.atlassian.performance.tools.infrastructure.api.jira.hook.install.InstalledJira
import com.atlassian.performance.tools.infrastructure.api.jira.hook.install.PostInstallHook
import com.atlassian.performance.tools.ssh.api.SshConnection

internal class DcHook(
    private val privateDcNodeIp: String
) : PostInstallHook {

    override fun run(ssh: SshConnection, jira: InstalledJira, hooks: PostInstallHooks) {
        val clusterProperties = "${jira.home}/cluster.properties"
        ssh.execute("echo ehcache.listener.hostName = $privateDcNodeIp >> $clusterProperties")
        ssh.execute("echo ehcache.object.port = 40011 >> $clusterProperties")
        ssh.execute("echo jira.node.id = ${jira.server.name} >> $clusterProperties")
    }
}
