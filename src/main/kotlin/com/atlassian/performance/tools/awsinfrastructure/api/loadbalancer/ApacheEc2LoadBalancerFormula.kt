package com.atlassian.performance.tools.awsinfrastructure.api.loadbalancer

import com.amazonaws.services.ec2.model.*
import com.atlassian.performance.tools.aws.api.*
import com.atlassian.performance.tools.awsinfrastructure.api.network.access.ForIpAccessRequester
import com.atlassian.performance.tools.awsinfrastructure.api.network.access.SecurityGroupIngressAccessProvider
import com.atlassian.performance.tools.jvmtasks.api.EventBus
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.net.URI
import java.util.function.Supplier

class ApacheEc2LoadBalancerFormula : LoadBalancerFormula {

    private val logger: Logger = LogManager.getLogger(this::class.java)
    private val balancerPort = 80

    /**
     * @param aws provides `shortTermStorageAccess` IAM role used for LB nodes
     */
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
        val securityGroup = aws.awaitingEc2.allocateSecurityGroup(
            investment,
            CreateSecurityGroupRequest()
                .withGroupName("${investment.reuseKey()}-HttpListener")
                .withDescription("Load balancer security group")
                .withVpcId(vpc.vpcId)
        )
        val (ssh, resource, instance) = aws.awaitingEc2.allocateInstance(
            investment = investment,
            key = key,
            vpcId = vpc.vpcId,
            customizeLaunch = { launch ->
                launch
                    .withInstanceInitiatedShutdownBehavior(ShutdownBehavior.Terminate)
                    .withSecurityGroupIds(securityGroup.groupId)
                    .withSubnetId(subnet.subnetId)
                    .withInstanceType(InstanceType.M5Large)
                    .withIamInstanceProfile(IamInstanceProfileSpecification().withName(aws.shortTermStorageAccess()))
            }
        )
        EventBus.publish(instance)
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
                    dependency = Ec2SecurityGroup(securityGroup, ec2)
                )
            )
            .accessProvider(
                SecurityGroupIngressAccessProvider
                    .Builder(ec2 = aws.ec2, securityGroup = securityGroup, portRange = balancerPort..balancerPort)
                    .build()
            )
            .accessRequester(ForIpAccessRequester(Supplier { instance.publicIpAddress }))
            .build()
    }
}
