package com.atlassian.performance.tools.awsinfrastructure.api.network

import com.amazonaws.services.ec2.model.Subnet
import com.amazonaws.services.ec2.model.Vpc

/**
 * @since 2.14.0
 */
class Network(
    val vpc: Vpc,
    val subnet: Subnet
) {
    override fun toString(): String {
        return "Network(vpc=$vpc, subnet=$subnet)"
    }
}
