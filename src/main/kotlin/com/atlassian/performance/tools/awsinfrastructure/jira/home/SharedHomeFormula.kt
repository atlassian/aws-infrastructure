package com.atlassian.performance.tools.awsinfrastructure.jira.home

import com.atlassian.performance.tools.aws.api.Storage
import com.atlassian.performance.tools.awsinfrastructure.api.aws.AwsCli
import com.atlassian.performance.tools.awsinfrastructure.api.hardware.Computer
import com.atlassian.performance.tools.infrastructure.api.jira.JiraHomeSource
import com.atlassian.performance.tools.infrastructure.api.jira.SharedHome
import com.atlassian.performance.tools.infrastructure.api.os.Ubuntu
import com.atlassian.performance.tools.ssh.api.Ssh
import java.time.Duration

internal class SharedHomeFormula(
    private val pluginsTransport: Storage,
    private val jiraHomeSource: JiraHomeSource,
    private val ip: String,
    private val ssh: Ssh,
    private val computer: Computer
) {
    private val localSubnet = "10.0.0.0/24"
    private val localSharedHome = "/home/ubuntu/jira-shared-home"

    private val ubuntu = Ubuntu()

    fun provision(): SharedHome {
        ssh.newConnection().use {
            computer.setUp(it)
            val jiraHome = jiraHomeSource.download(it)
            AwsCli().download(
                location = pluginsTransport.location,
                ssh = it,
                target = "$jiraHome/plugins/installed-plugins",
                timeout = Duration.ofMinutes(3)
            )

            it.execute("sudo mkdir -p $localSharedHome")
            it.safeExecute("sudo mv $jiraHome/{data,plugins,import,export} $localSharedHome")
            it.safeExecute("sudo mv $jiraHome/logos $localSharedHome")
            ubuntu.install(it, listOf("nfs-kernel-server"))
            it.execute("sudo echo '$localSharedHome $localSubnet(rw,sync,no_subtree_check,no_root_squash)' | sudo tee -a /etc/exports")
            it.execute("sudo service nfs-kernel-server restart")
        }

        return SharedHome(ip, localSharedHome)
    }
}

