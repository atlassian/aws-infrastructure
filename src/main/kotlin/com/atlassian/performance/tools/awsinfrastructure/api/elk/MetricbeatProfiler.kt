package com.atlassian.performance.tools.awsinfrastructure.api.elk

import com.atlassian.performance.tools.infrastructure.api.process.RemoteMonitoringProcess
import com.atlassian.performance.tools.infrastructure.api.profiler.Profiler
import com.atlassian.performance.tools.ssh.api.SshConnection

class MetricbeatProfiler(
    private val metricbeat: UbuntuMetricbeat
) : Profiler {

    override fun install(ssh: SshConnection) {
        metricbeat.install(ssh)
    }

    override fun start(ssh: SshConnection, pid: Int): RemoteMonitoringProcess? {
        return null
    }
}
