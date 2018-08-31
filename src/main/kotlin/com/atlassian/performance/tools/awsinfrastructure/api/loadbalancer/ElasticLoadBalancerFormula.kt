package com.atlassian.performance.tools.awsinfrastructure.api.loadbalancer

import com.amazonaws.services.cloudformation.model.Parameter
import com.amazonaws.services.ec2.model.Instance
import com.amazonaws.services.ec2.model.Subnet
import com.amazonaws.services.ec2.model.Vpc
import com.atlassian.performance.tools.aws.Aws
import com.atlassian.performance.tools.aws.Investment
import com.atlassian.performance.tools.aws.SshKey
import com.atlassian.performance.tools.aws.StackFormula
import com.atlassian.performance.tools.awsinfrastructure.loadbalancer.ElasticLoadBalancer
import com.atlassian.performance.tools.io.readResourceText
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

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
        logger.info("Elastic load balancer is set up")
        return ProvisionedLoadBalancer(
            loadBalancer = ElasticLoadBalancer(
                aws.loadBalancer,
                stack.findLoadBalancer()
            ),
            resource = stack
        )
    }
}