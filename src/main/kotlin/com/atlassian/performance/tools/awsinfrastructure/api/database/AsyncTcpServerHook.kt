package com.atlassian.performance.tools.awsinfrastructure.api.database

import com.atlassian.performance.tools.infrastructure.api.jira.flow.JiraNodeFlow
import com.atlassian.performance.tools.infrastructure.api.jira.flow.TcpServer
import com.atlassian.performance.tools.infrastructure.api.jira.flow.server.TcpServerHook
import com.atlassian.performance.tools.ssh.api.SshConnection
import java.util.concurrent.Executors

class AsyncTcpServerHook(
    private val hook: TcpServerHook
) : TcpServerHook {

    override fun run(ssh: SshConnection, server: TcpServer, flow: JiraNodeFlow) {
        val thread = Executors.newSingleThreadExecutor()
        val future = thread.submit {
            hook.run(ssh, server, flow)
        }
        flow.hookPostInstall(FutureBlockingHook(future))
    }
}
