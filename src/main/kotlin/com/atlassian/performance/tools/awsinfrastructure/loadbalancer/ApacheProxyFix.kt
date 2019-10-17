package com.atlassian.performance.tools.awsinfrastructure.loadbalancer

import com.atlassian.performance.tools.infrastructure.api.Sed
import com.atlassian.performance.tools.infrastructure.api.jira.hook.PreStartHook
import com.atlassian.performance.tools.infrastructure.api.jira.hook.PreStartHooks
import com.atlassian.performance.tools.infrastructure.api.jira.hook.install.InstalledJira
import com.atlassian.performance.tools.ssh.api.SshConnection
import java.net.URI

class ApacheProxyFix(
    private val loadBalancer: URI
) : PreStartHook {

    override fun run(ssh: SshConnection, jira: InstalledJira, hooks: PreStartHooks) {
        Sed().replace(
            ssh,
            "bindOnInit=\"false\"",
            "bindOnInit=\"false\" scheme=\"http\" proxyName=\"${loadBalancer.host}\" proxyPort=\"80\"",
            "${jira.installation}/conf/server.xml"
        )
    }
}
