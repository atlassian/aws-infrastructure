package com.atlassian.performance.tools.awsinfrastructure.api.network

import com.atlassian.performance.tools.aws.api.Resource

class ProvisionedNetwork(
    network: Network,
    private val resource: Resource
) : Network(network.vpc, network.subnet), Resource by resource {

    override fun toString(): String {
        return "ProvisionedNetwork(network=${super.toString()}, resource=$resource)"
    }
}
