package com.atlassian.performance.tools.awsinfrastructure.api.loadbalancer

import com.amazonaws.services.cloudformation.model.Parameter
import com.amazonaws.services.ec2.model.Instance
import com.amazonaws.services.ec2.model.Subnet
import com.amazonaws.services.ec2.model.Vpc
import com.atlassian.performance.tools.aws.api.Aws
import com.atlassian.performance.tools.aws.api.Investment
import com.atlassian.performance.tools.aws.api.SshKey
import com.atlassian.performance.tools.aws.api.StackFormula
import com.atlassian.performance.tools.awsinfrastructure.api.network.access.ForSecurityGroupAccessRequester
import com.atlassian.performance.tools.awsinfrastructure.api.network.access.SecurityGroupIngressAccessProvider
import com.atlassian.performance.tools.awsinfrastructure.loadbalancer.ElasticLoadBalancer
import com.atlassian.performance.tools.io.api.readResourceText
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.util.function.Supplier

class ElasticLoadBalancerFormula : LoadBalancerFormula {
    private val logger: Logger = LogManager.getLogger(this::class.java)

    override fun provision(
        investment: Investment,
        instances: List<Instance>,
        vpc: Vpc,
        subnet: Subnet,
        key: SshKey,
        aws: Aws
    ): ProvisionedLoadBalancer {
        logger.info("Setting up elastic load balancer...")
        val stack = StackFormula(
            investment = investment,
            cloudformationTemplate = readResourceText("aws/load-balancer.yaml"),
            parameters = listOf(
                Parameter()
                    .withParameterKey("Instances")
                    .withParameterValue(instances.joinToString(separator = ",") { it.instanceId }),
                Parameter()
                    .withParameterKey("Vpc")
                    .withParameterValue(vpc.vpcId),
                Parameter()
                    .withParameterKey("Subnet")
                    .withParameterValue(subnet.subnetId)
            ),
            aws = aws
        ).provision()
        val securityGroup = stack.findSecurityGroup("LoadBalancerSecurityGroup")

        logger.info("Elastic load balancer is set up")
        return ProvisionedLoadBalancer
            .Builder(
                ElasticLoadBalancer(
                    aws.loadBalancer,
                    stack.findLoadBalancer()
                )
            )
            .resource(stack)
            .accessProvider(
                SecurityGroupIngressAccessProvider
                    .Builder(ec2 = aws.ec2, securityGroup = securityGroup, portRange = 80..80).build()
            )
            .accessRequester(ForSecurityGroupAccessRequester(Supplier { securityGroup }))
            .build()
    }
}
