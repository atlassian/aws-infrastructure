package com.atlassian.performance.tools.awsinfrastructure.jira

import com.atlassian.performance.tools.awsinfrastructure.api.jira.StartedNode
import com.atlassian.performance.tools.ssh.Ssh

internal interface StoppedNode {
    val jiraHome: String
    val ssh: Ssh
    fun start(): StartedNode
}