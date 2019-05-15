package com.atlassian.performance.tools.awsinfrastructure.api.kibana

import com.atlassian.performance.tools.infrastructure.api.os.Ubuntu
import com.atlassian.performance.tools.ssh.api.SshConnection
import java.net.URI

class UbuntuElasticsearch {

    fun install(
        shell: SshConnection,
        port: Int
    ): URI {
        Ubuntu().install(shell, listOf("wget"))
        val debFile = "elasticsearch-7.0.1-amd64.deb"
        shell.execute("wget https://artifacts.elastic.co/downloads/elasticsearch/$debFile -q --server-response")
        shell.execute("sudo dpkg -i $debFile")
        val config = ElasticConfig("elasticsearch")
        val ip = shell.getHost().ipAddress
        config.append("network.host: 0.0.0.0", shell)
        config.append("http.port: $port", shell)
        config.append("node.name: node-1", shell)
        config.append("discovery.seed_hosts: ['127.0.0.1', '[::1]']", shell)
        config.append("cluster.initial_master_nodes: ['node-1']", shell)
        shell.execute("sudo systemctl start elasticsearch.service")
        return URI("http://$ip:$port")
    }
}
