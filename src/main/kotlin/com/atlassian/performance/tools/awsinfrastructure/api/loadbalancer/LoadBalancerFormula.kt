package com.atlassian.performance.tools.awsinfrastructure.api.loadbalancer

import com.amazonaws.services.ec2.model.Instance
import com.amazonaws.services.ec2.model.Subnet
import com.amazonaws.services.ec2.model.Vpc
import com.atlassian.performance.tools.aws.api.Aws
import com.atlassian.performance.tools.aws.api.Investment
import com.atlassian.performance.tools.aws.api.SshKey

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
