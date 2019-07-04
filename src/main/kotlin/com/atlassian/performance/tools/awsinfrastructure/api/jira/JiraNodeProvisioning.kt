package com.atlassian.performance.tools.awsinfrastructure.api.jira

import com.atlassian.performance.tools.infrastructure.api.distribution.PublicJiraSoftwareDistribution
import com.atlassian.performance.tools.infrastructure.api.jira.EmptyJiraHome
import com.atlassian.performance.tools.infrastructure.api.jira.JiraNodeConfig
import com.atlassian.performance.tools.infrastructure.api.jira.flow.JiraNodeFlow
import com.atlassian.performance.tools.infrastructure.api.jira.flow.install.DefaultJiraInstallation
import com.atlassian.performance.tools.infrastructure.api.jira.flow.install.DefaultPostInstallHook
import com.atlassian.performance.tools.infrastructure.api.jira.flow.install.HookedJiraInstallation
import com.atlassian.performance.tools.infrastructure.api.jira.flow.install.JiraInstallation
import com.atlassian.performance.tools.infrastructure.api.jira.flow.start.DefaultStartedJiraHook
import com.atlassian.performance.tools.infrastructure.api.jira.flow.start.HookedJiraStart
import com.atlassian.performance.tools.infrastructure.api.jira.flow.start.JiraLaunchScript
import com.atlassian.performance.tools.infrastructure.api.jira.flow.start.JiraStart
import com.atlassian.performance.tools.infrastructure.api.jvm.OracleJDK
import net.jcip.annotations.NotThreadSafe

class JiraNodeProvisioning private constructor(
    val flow: JiraNodeFlow,
    val installation: JiraInstallation,
    val start: JiraStart
) {

    @NotThreadSafe
    class Builder {
        private var flow: JiraNodeFlow = JiraNodeFlow().apply {
            hookPostStart(
                DefaultStartedJiraHook()
            )
            hookPostInstall(
                DefaultPostInstallHook(
                    JiraNodeConfig.Builder().build()
                )
            )
        }
        private var installation: JiraInstallation = HookedJiraInstallation(
            DefaultJiraInstallation(
                EmptyJiraHome(),
                PublicJiraSoftwareDistribution("7.13.0"),
                OracleJDK()
            )
        )
        private var start: JiraStart = HookedJiraStart(
            JiraLaunchScript()
        )

        fun flow(flow: JiraNodeFlow) = apply { this.flow = flow }
        fun installation(installation: JiraInstallation) = apply { this.installation = installation }
        fun start(start: JiraStart) = apply { this.start = start }

        fun build() = JiraNodeProvisioning(
            flow = flow,
            installation = installation,
            start = start
        )
    }
}
