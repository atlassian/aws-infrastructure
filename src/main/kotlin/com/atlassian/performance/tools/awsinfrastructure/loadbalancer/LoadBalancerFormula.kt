package com.atlassian.performance.tools.awsinfrastructure.loadbalancer

import com.amazonaws.services.ec2.model.Instance
import com.amazonaws.services.ec2.model.Subnet
import com.amazonaws.services.ec2.model.Vpc
import com.atlassian.performance.tools.aws.Aws
import com.atlassian.performance.tools.aws.Investment
import com.atlassian.performance.tools.aws.Resource
import com.atlassian.performance.tools.aws.SshKey
import com.atlassian.performance.tools.infrastructure.loadbalancer.LoadBalancer

interface LoadBalancerFormula {

    fun provision(
        investment: Investment,
        instances: List<Instance>,
        vpc: Vpc,
        subnet: Subnet,
        key: SshKey,
        aws: Aws
    ): ProvisionedLoadBalancer
}

class ProvisionedLoadBalancer(
    val loadBalancer: LoadBalancer,
    val resource: Resource
)