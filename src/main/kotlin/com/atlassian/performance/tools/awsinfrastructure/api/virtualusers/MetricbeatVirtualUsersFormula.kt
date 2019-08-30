package com.atlassian.performance.tools.awsinfrastructure.api.virtualusers

import com.atlassian.performance.tools.aws.api.Aws
import com.atlassian.performance.tools.aws.api.Investment
import com.atlassian.performance.tools.aws.api.SshKey
import com.atlassian.performance.tools.aws.api.Storage
import com.atlassian.performance.tools.awsinfrastructure.api.elk.UbuntuMetricbeat
import com.atlassian.performance.tools.infrastructure.api.virtualusers.ResultsTransport
import com.atlassian.performance.tools.infrastructure.api.virtualusers.SshVirtualUsers
import java.util.concurrent.Future

class MetricbeatVirtualUsersFormula(
    private val base: VirtualUsersFormula2<SshVirtualUsers>,
    private val metricbeat: UbuntuMetricbeat,
    private val filebeat: UbuntuFilebeat
) : VirtualUsersFormula2<SshVirtualUsers> {

    override fun provision(
        investment: Investment,
        shadowJarTransport: Storage,
        resultsTransport: ResultsTransport,
        key: Future<SshKey>,
        roleProfile: String,
        aws: Aws,
        nodeNumber: Int
    ): ProvisionedVirtualUsers<SshVirtualUsers> {
        val sshVus = base.provision(
            investment, shadowJarTransport, resultsTransport, key, roleProfile, aws, nodeNumber
        )
        sshVus.virtualUsers.ssh.newConnection().use { ssh ->
            filebeat.install(ssh)
            metricbeat.install(ssh)
        }
        return sshVus
    }
}
