package com.atlassian.performance.tools.awsinfrastructure.jira.home

import com.atlassian.performance.tools.aws.Storage
import com.atlassian.performance.tools.awsinfrastructure.AwsCli
import com.atlassian.performance.tools.infrastructure.jira.home.JiraHomeSource
import com.atlassian.performance.tools.infrastructure.jira.home.SharedHome
import com.atlassian.performance.tools.infrastructure.os.Ubuntu
import com.atlassian.performance.tools.ssh.Ssh
import java.time.Duration

class SharedHomeFormula(
    private val pluginsTransport: Storage,
    private val jiraHomeSource: JiraHomeSource,
    private val ip: String,
    private val ssh: Ssh
) {
    private val localSubnet = "10.0.0.0/24"
    private val localSharedHome = "/opt/jira-shared-home"

    private val ubuntu = Ubuntu()

    fun provision(): SharedHome {
        ssh.newConnection().use {
            val jiraHome = jiraHomeSource.download(it)
            AwsCli().download(
                location = pluginsTransport.location,
                ssh = it,
                target = "$jiraHome/plugins/installed-plugins",
                timeout = Duration.ofMinutes(3)
            )

            it.execute("sudo mkdir -p $localSharedHome")
            it.execute("sudo mv $jiraHome/{data,plugins,import,export} $localSharedHome")
            it.safeExecute("sudo mv $jiraHome/logos $localSharedHome")
            ubuntu.install(it, listOf("nfs-kernel-server"))
            it.execute("sudo echo '$localSharedHome $localSubnet(rw,sync,no_subtree_check,no_root_squash)' | sudo tee -a /etc/exports")
            it.execute("sudo service nfs-kernel-server restart")
        }

        return SharedHome(ip, localSharedHome)
    }
}

