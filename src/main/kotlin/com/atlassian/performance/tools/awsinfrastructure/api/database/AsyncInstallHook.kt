package com.atlassian.performance.tools.awsinfrastructure.api.database

import com.atlassian.performance.tools.concurrency.api.submitWithLogContext
import com.atlassian.performance.tools.infrastructure.api.jira.hook.PostInstallHooks
import com.atlassian.performance.tools.infrastructure.api.jira.hook.PreInstallHooks
import com.atlassian.performance.tools.infrastructure.api.jira.hook.TcpServer
import com.atlassian.performance.tools.infrastructure.api.jira.hook.install.InstalledJira
import com.atlassian.performance.tools.infrastructure.api.jira.hook.install.PostInstallHook
import com.atlassian.performance.tools.infrastructure.api.jira.hook.server.PreInstallHook
import com.atlassian.performance.tools.ssh.api.SshConnection
import java.util.concurrent.Executors
import java.util.concurrent.Future

/**
 * Begins to run [hook] during [PreInstallHooks] and finishes during [PostInstallHooks]
 */
class AsyncInstallHook(
    private val hook: PreInstallHook
) : PreInstallHook {

    override fun run(ssh: SshConnection, server: TcpServer, hooks: PreInstallHooks) {
        val thread = Executors.newSingleThreadExecutor()
        val future = thread.submitWithLogContext("async-hook") {
            hook.run(ssh, server, hooks)
        }
        hooks.hook(FutureBlockingHook(future))
    }
}

private class FutureBlockingHook(
    private val future: Future<*>
) : PostInstallHook {

    override fun run(ssh: SshConnection, jira: InstalledJira, hooks: PostInstallHooks) {
        future.get()
    }
}
