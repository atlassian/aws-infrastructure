package com.atlassian.performance.tools.awsinfrastructure.jira

import com.atlassian.performance.tools.awsinfrastructure.api.jira.StartedNode
import com.atlassian.performance.tools.ssh.api.Ssh

internal interface StoppedNode {
    val jiraHome: String
    val ssh: Ssh
    fun start(
        updateConfigurationFunction: List<(ssh: Ssh, unpackedProduct: String) -> Unit>
    ): StartedNode
}