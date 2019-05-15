package com.atlassian.performance.tools.awsinfrastructure.api.kibana

import com.atlassian.performance.tools.infrastructure.api.os.Ubuntu
import com.atlassian.performance.tools.infrastructure.api.process.RemoteMonitoringProcess
import com.atlassian.performance.tools.infrastructure.api.profiler.Profiler
import com.atlassian.performance.tools.ssh.api.SshConnection
import java.time.Duration

class MetricbeatProfiler(
    private val kibana: Kibana,
    private val fields: Map<String, Any>
) : Profiler {

    override fun install(ssh: SshConnection) {
        Ubuntu().install(ssh, listOf("wget"))
        val debFile = "metricbeat-7.0.1-amd64.deb"
        ssh.execute("wget https://artifacts.elastic.co/downloads/beats/metricbeat/$debFile -q --server-response")
        ssh.execute("sudo dpkg -i $debFile", Duration.ofSeconds(50))
        ssh.execute("sudo metricbeat modules enable system")
        val config = ElasticConfig("metricbeat")
        config.append("setup.kibana.host: '${kibana.address}'", ssh)
        val hostsYaml = kibana
            .elasticsearchHosts
            .map { it.toString() }
            .let { config.toYamlArray(it) }
        config.append("output.elasticsearch.hosts: $hostsYaml", ssh)
        config.append("fields: ${config.toYamlDictionary(fields)}", ssh)
        ssh.execute("sudo metricbeat setup --dashboards", Duration.ofSeconds(70))
        ssh.execute("sudo service metricbeat start")
    }

    override fun start(ssh: SshConnection, pid: Int): RemoteMonitoringProcess? {
        return null
    }
}
