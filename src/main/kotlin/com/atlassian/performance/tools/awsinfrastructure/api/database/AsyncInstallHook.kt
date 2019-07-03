package com.atlassian.performance.tools.awsinfrastructure.api.database

import com.atlassian.performance.tools.concurrency.api.submitWithLogContext
import com.atlassian.performance.tools.infrastructure.api.jira.install.HttpNode
import com.atlassian.performance.tools.infrastructure.api.jira.install.InstalledJira
import com.atlassian.performance.tools.infrastructure.api.jira.install.hook.PostInstallHook
import com.atlassian.performance.tools.infrastructure.api.jira.install.hook.PostInstallHooks
import com.atlassian.performance.tools.infrastructure.api.jira.install.hook.PreInstallHook
import com.atlassian.performance.tools.infrastructure.api.jira.install.hook.PreInstallHooks
import com.atlassian.performance.tools.infrastructure.api.jira.report.Reports
import com.atlassian.performance.tools.ssh.api.SshConnection
import java.util.concurrent.Executors
import java.util.concurrent.Future

/**
 * Begins to run [hook] during [PreInstallHooks] and finishes during [PostInstallHooks]
 */
class AsyncInstallHook(
    private val hook: PreInstallHook
) : PreInstallHook {

    override fun call(ssh: SshConnection, http: HttpNode, hooks: PreInstallHooks, reports: Reports) {
        val thread = Executors.newSingleThreadExecutor()
        val future = thread.submitWithLogContext("async-hook") {
            hook.call(ssh, http, hooks, reports)
        }
        hooks.postInstall.insert(FutureHook(future))
    }
}

private class FutureHook(
    private val future: Future<*>
) : PostInstallHook {

    override fun call(ssh: SshConnection, jira: InstalledJira, hooks: PostInstallHooks, reports: Reports) {
        future.get()
    }
}
