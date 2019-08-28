package com.atlassian.performance.tools.awsinfrastructure.api.virtualusers

import com.atlassian.performance.tools.aws.api.Aws
import com.atlassian.performance.tools.aws.api.Investment
import com.atlassian.performance.tools.aws.api.SshKey
import com.atlassian.performance.tools.aws.api.Storage
import com.atlassian.performance.tools.awsinfrastructure.api.kibana.UbuntuMetricbeat
import com.atlassian.performance.tools.infrastructure.api.virtualusers.ResultsTransport
import com.atlassian.performance.tools.infrastructure.api.virtualusers.SshVirtualUsers
import java.util.concurrent.Future

class MetricbeatVirtualUsersFormula(
    private val base: VirtualUsersFormula<SshVirtualUsers>,
    private val metricbeat: UbuntuMetricbeat
) : VirtualUsersFormula<SshVirtualUsers> {

    override fun provision(
        investment: Investment,
        shadowJarTransport: Storage,
        resultsTransport: ResultsTransport,
        key: Future<SshKey>,
        roleProfile: String,
        aws: Aws
    ): ProvisionedVirtualUsers<SshVirtualUsers> {
        val sshVus = base.provision(
            investment, shadowJarTransport, resultsTransport, key, roleProfile, aws
        )
        sshVus.virtualUsers.ssh.newConnection().use { ssh ->
            metricbeat.install(ssh)
        }
        return sshVus
    }
}
