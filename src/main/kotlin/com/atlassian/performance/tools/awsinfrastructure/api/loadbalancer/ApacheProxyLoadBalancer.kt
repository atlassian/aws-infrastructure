package com.atlassian.performance.tools.awsinfrastructure.api.loadbalancer

import com.atlassian.performance.tools.aws.api.StorageLocation
import com.atlassian.performance.tools.awsinfrastructure.api.aws.AwsCli
import com.atlassian.performance.tools.infrastructure.api.Sed
import com.atlassian.performance.tools.jvmtasks.api.ExponentialBackoff
import com.atlassian.performance.tools.jvmtasks.api.IdempotentAction
import com.atlassian.performance.tools.ssh.api.Ssh
import com.atlassian.performance.tools.ssh.api.SshConnection
import java.net.URI
import java.time.Duration

class ApacheProxyLoadBalancer private constructor(
    private val nodes: List<URI>,
    private val ssh: Ssh,
    ipAddress: String,
    httpPort: Int
) : DiagnosableLoadBalancer {

    override val uri: URI = URI("http://${ipAddress}:$httpPort/")

    override fun waitUntilHealthy(
        timeout: Duration
    ) {

    }

    private val APACHE_CONFIG_PATH = "/etc/apache2/sites-enabled/000-default.conf"

    fun provision() {
        IdempotentAction("Installing and configuring apache load balancer") {
            tryToProvision(
                ssh
            )
        }
            .retry(
                maxAttempts = 2,
                backoff = ExponentialBackoff(
                    baseBackoff = Duration.ofSeconds(5)
                )
            )
    }

    fun updateJiraConfiguration(
        ssh: Ssh,
        unpackedProduct: String
    ) {
        ssh.newConnection().use { shell ->
            Sed().replace(
                shell,
                "bindOnInit=\"false\"",
                "bindOnInit=\"false\" scheme=\"http\" proxyName=\"${uri.host}\" proxyPort=\"80\"",
                "$unpackedProduct/conf/server.xml")
        }
    }

    fun tryToProvision(ssh: Ssh) {
        ssh.newConnection().use { connection ->
            connection.execute("sudo apt-get update", Duration.ofMinutes(3))
            connection.execute("sudo apt-get --assume-yes install apache2", Duration.ofMinutes(1))
            connection.execute("sudo rm $APACHE_CONFIG_PATH")
            connection.execute("sudo touch $APACHE_CONFIG_PATH")
            connection.execute("sudo a2enmod proxy proxy_ajp proxy_http rewrite deflate headers proxy_balancer proxy_connect proxy_html xml2enc lbmethod_byrequests")

            appendToApacheProxyConfiguration(connection, "Header add Set-Cookie \\\"ROUTEID=.%{BALANCER_WORKER_ROUTE}e; path=/\\\" env=BALANCER_ROUTE_CHANGED")
            appendToApacheProxyConfiguration(connection, "<Proxy balancer://mycluster>")

            nodes.forEachIndexed { index, uri -> appendToApacheProxyConfiguration(connection, "\tBalancerMember http://${uri.host}:${uri.port} route=$index")}

            appendToApacheProxyConfiguration(connection, "</Proxy>\n")
            appendToApacheProxyConfiguration(connection, "ProxyPass / balancer://mycluster/ stickysession=ROUTEID")
            appendToApacheProxyConfiguration(connection, "ProxyPassReverse / balancer://mycluster/ stickysession=ROUTEID")

            connection.execute("sudo service apache2 restart", Duration.ofMinutes(3))
        }
    }

    private fun appendToApacheProxyConfiguration(
        connection: SshConnection,
        line: String
    ) {
        connection.execute("echo \"$line\" | sudo tee -a $APACHE_CONFIG_PATH")
    }

    override fun gatherEvidence(location: StorageLocation) {
        ssh.newConnection().use { connection ->
            val resultsDir = "/tmp/s3-results"
            connection.execute("mkdir -p $resultsDir")
            connection.execute("cp -R /var/log/apache2 $resultsDir")
            AwsCli().upload(location, connection, resultsDir, Duration.ofMinutes(1))
        }
    }

    class Builder(
        private val ssh: Ssh
    ) {
        private var nodes: List<URI> = emptyList()
        private var ipAddress: String = ssh.host.ipAddress
        private var httpPort: Int = 80

        fun nodes(nodes: List<URI>) = apply { this.nodes = nodes }
        fun ipAddress(ipAddress: String) = apply { this.ipAddress = ipAddress }
        fun httpPort(httpPort: Int) = apply { this.httpPort = httpPort }

        fun build() = ApacheProxyLoadBalancer(
            nodes = nodes,
            ssh = ssh,
            ipAddress = ipAddress,
            httpPort = httpPort
        )
    }
}
