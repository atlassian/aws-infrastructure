package com.atlassian.performance.tools.awsinfrastructure.api.kibana

import com.atlassian.performance.tools.infrastructure.api.os.Ubuntu
import com.atlassian.performance.tools.ssh.api.SshConnection
import java.net.URI
import java.time.Duration

class UbuntuKibana {

    fun install(
        shell: SshConnection,
        port: Int,
        elasticsearchHosts: List<URI>
    ): Kibana {
        Ubuntu().install(shell, listOf("wget"))
        val debFile = "kibana-7.0.1-amd64.deb"
        shell.execute("wget https://artifacts.elastic.co/downloads/kibana/$debFile -q --server-response")
        shell.execute("sudo dpkg -i $debFile", Duration.ofSeconds(50))
        val config = ElasticConfig("kibana")
        val ip = shell.getHost().ipAddress
        config.append("server.host: 0.0.0.0", shell)
        config.append("server.port: $port", shell)
        val hosts = elasticsearchHosts
            .map { it.toString() }
            .let { config.toYamlArray(it) }
        config.append("elasticsearch.hosts: $hosts", shell)
        shell.execute("sudo systemctl start kibana.service")
        return Kibana(
            address = URI("http://$ip:$port"),
            elasticsearchHosts = elasticsearchHosts
        )
    }
}
