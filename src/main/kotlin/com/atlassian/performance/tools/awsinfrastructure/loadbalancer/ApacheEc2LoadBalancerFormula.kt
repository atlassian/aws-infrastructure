package com.atlassian.performance.tools.awsinfrastructure.loadbalancer

import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.model.*
import com.atlassian.performance.tools.aws.api.*
import com.atlassian.performance.tools.awsinfrastructure.api.loadbalancer.LoadBalancerFormula
import com.atlassian.performance.tools.awsinfrastructure.api.loadbalancer.ProvisionedLoadBalancer
import com.atlassian.performance.tools.infrastructure.api.loadbalancer.LoadBalancer
import com.atlassian.performance.tools.ssh.api.Ssh
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.net.URI
import java.time.Duration

internal class ApacheEc2LoadBalancerFormula : LoadBalancerFormula {
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
                    .withInstanceType(InstanceType.M1Large)
            }
        )
        logger.info("Apache load balancer is set up")
        return ProvisionedLoadBalancer(
            loadBalancer = ApacheLoadBalancer(
                nodes = instances.map { URI("http://${it.publicIpAddress}:8080/") },
                httpPort = balancerPort,
                ssh = ssh
            ),
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

private class ApacheLoadBalancer(
    private val nodes: List<URI>,
    private val ssh: Ssh,
    private val httpPort: Int
) : LoadBalancer {

    init {
        throw Exception("Wait until the `infrastructure` module ships `ApacheLoadBalancer`")
    }

    override val uri: URI = URI("http://${ssh.host.ipAddress}:$httpPort/")

    override fun waitUntilHealthy(
        timeout: Duration
    ) {
        throw Exception(
            "TODO: Install Apache load balancer via $ssh," +
                " configure it to balance between $nodes" +
                " and listen on $httpPort"
        )
    }
}