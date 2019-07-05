package com.atlassian.performance.tools.awsinfrastructure.api.jira

import com.atlassian.performance.tools.aws.api.Storage
import com.atlassian.performance.tools.awsinfrastructure.AwsCli
import com.atlassian.performance.tools.infrastructure.api.app.AppSource
import com.atlassian.performance.tools.infrastructure.api.app.Apps
import com.atlassian.performance.tools.infrastructure.api.jira.flow.JiraNodeFlow
import com.atlassian.performance.tools.infrastructure.api.jira.flow.install.InstalledJira
import com.atlassian.performance.tools.infrastructure.api.jira.flow.install.InstalledJiraHook
import com.atlassian.performance.tools.ssh.api.SshConnection

class S3AppSourceInstall(
    private val apps: List<AppSource>,
    private val transport: Storage
) : InstalledJiraHook {

    override fun run(ssh: SshConnection, jira: InstalledJira, flow: JiraNodeFlow) {
        Apps(apps)
            .listFiles()
            .forEach { transport.upload(it) }
        AwsCli().download(
            transport.location,
            ssh,
            "${jira.home}/plugins/installed-plugins"
        )
    }
}