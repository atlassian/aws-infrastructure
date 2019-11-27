package com.atlassian.performance.tools.awsinfrastructure.api.loadbalancer

import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.model.*
import com.atlassian.performance.tools.aws.api.*
import com.atlassian.performance.tools.awsinfrastructure.api.virtualusers.InstanceAddressSelector
import com.atlassian.performance.tools.infrastructure.api.Sed
import com.atlassian.performance.tools.infrastructure.api.loadbalancer.LoadBalancer
import com.atlassian.performance.tools.jvmtasks.api.ExponentialBackoff
import com.atlassian.performance.tools.jvmtasks.api.IdempotentAction
import com.atlassian.performance.tools.ssh.api.Ssh
import com.atlassian.performance.tools.ssh.api.SshConnection
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.net.URI
import java.time.Duration

class ApacheEc2LoadBalancerFormula : LoadBalancerFormula {

    private val logger: Logger = LogManager.getLogger(this::class.java)
    private val balancerPort = 80

    override fun provision(
        investment: Investment,
        instances: List<Instance>,
        vpc: Vpc,
        subnet: Subnet,
        key: SshKey,
        aws: Aws
    ): ProvisionedLoadBalancer {
        logger.info("Setting up Apache load balancer...")
        val ec2 = aws.ec2
        val httpAccess = httpAccess(investment, ec2, aws.awaitingEc2, vpc)
        val (ssh, resource) = aws.awaitingEc2.allocateInstance(
            investment = investment,
            key = key,
            vpcId = vpc.vpcId,
            customizeLaunch = { launch ->
                launch
                    .withSecurityGroupIds(httpAccess.groupId)
                    .withSubnetId(subnet.subnetId)
                    .withInstanceType(InstanceType.M5Large)
            }
        )
        key.file.facilitateSsh(ssh.host.ipAddress)
        val loadBalancer = ApacheProxyLoadBalancer(
            nodes = instances.map {
                val ipAddress = InstanceAddressSelector.getReachableIpAddress(it)
                URI("http://$ipAddress:8080/")
            },
            httpPort = balancerPort,
            ssh = ssh
        )
        loadBalancer.provision()
        logger.info("Apache load balancer is set up")
        return ProvisionedLoadBalancer(
            loadBalancer = loadBalancer,
            resource = DependentResources(
                user = resource,
                dependency = Ec2SecurityGroup(httpAccess, ec2)
            )

        )
    }

    private fun httpAccess(
        investment: Investment,
        ec2: AmazonEC2,
        awaitingEc2: AwaitingEc2,
        vpc: Vpc
    ): SecurityGroup {
        val securityGroup = awaitingEc2.allocateSecurityGroup(
            investment,
            CreateSecurityGroupRequest()
                .withGroupName("${investment.reuseKey()}-HttpListener")
                .withDescription("Enables HTTP access")
                .withVpcId(vpc.vpcId)
        )
        ec2.authorizeSecurityGroupIngress(
            AuthorizeSecurityGroupIngressRequest()
                .withGroupId(securityGroup.groupId)
                .withIpPermissions(
                    IpPermission()
                        .withIpProtocol("tcp")
                        .withFromPort(balancerPort)
                        .withToPort(balancerPort)
                        .withIpv4Ranges(
                            IpRange().withCidrIp("0.0.0.0/0")
                        )
                )
        )
        return securityGroup
    }
}

internal class ApacheProxyLoadBalancer(
    private val nodes: List<URI>,
    private val ssh: Ssh,
    httpPort: Int
) : LoadBalancer {

    override val uri: URI = URI("http://${ssh.host.ipAddress}:$httpPort/")

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


}

