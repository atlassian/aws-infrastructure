package com.atlassian.performance.tools.awsinfrastructure.api.loadbalancer

import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.model.*
import com.atlassian.performance.tools.aws.api.*
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.net.URI

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
        val (ssh, resource, instance) = aws.awaitingEc2.allocateInstance(
            investment = investment,
            key = key,
            vpcId = vpc.vpcId,
            customizeLaunch = { launch ->
                launch
                    .withInstanceInitiatedShutdownBehavior(ShutdownBehavior.Terminate)
                    .withSecurityGroupIds(httpAccess.groupId)
                    .withSubnetId(subnet.subnetId)
                    .withInstanceType(InstanceType.M5Large)
            }
        )
        key.file.facilitateSsh(ssh.host.ipAddress)
        val loadBalancer = ApacheProxyLoadBalancer.Builder(ssh)
            .nodes(instances.map { URI("http://${it.publicIpAddress}:8080/") })
            .ipAddress(instance.publicIpAddress)
            .build()
        loadBalancer.provision()
        logger.info("Apache load balancer is set up")
        return ProvisionedLoadBalancer.Builder(loadBalancer)
            .resource(
                DependentResources(
                    user = resource,
                    dependency = Ec2SecurityGroup(httpAccess, ec2)
                )
            )
            .build()
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
