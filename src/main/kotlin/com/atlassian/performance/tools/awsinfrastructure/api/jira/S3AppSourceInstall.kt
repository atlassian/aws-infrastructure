package com.atlassian.performance.tools.awsinfrastructure.api.jira

import com.atlassian.performance.tools.aws.api.Storage
import com.atlassian.performance.tools.awsinfrastructure.AwsCli
import com.atlassian.performance.tools.infrastructure.api.app.AppSource
import com.atlassian.performance.tools.infrastructure.api.app.Apps
import com.atlassian.performance.tools.infrastructure.api.jira.install.InstalledJira
import com.atlassian.performance.tools.infrastructure.api.jira.install.hook.PostInstallHook
import com.atlassian.performance.tools.infrastructure.api.jira.install.hook.PostInstallHooks
import com.atlassian.performance.tools.infrastructure.api.jira.report.Reports
import com.atlassian.performance.tools.ssh.api.SshConnection

class S3AppSourceInstall(
    private val apps: List<AppSource>,
    private val transport: Storage
) : PostInstallHook {

    override fun call(ssh: SshConnection, jira: InstalledJira, hooks: PostInstallHooks, reports: Reports) {
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
