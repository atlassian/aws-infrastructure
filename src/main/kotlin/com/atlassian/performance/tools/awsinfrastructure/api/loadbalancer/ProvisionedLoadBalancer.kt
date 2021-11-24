package com.atlassian.performance.tools.awsinfrastructure.api.loadbalancer

import com.atlassian.performance.tools.aws.api.Resource
import com.atlassian.performance.tools.aws.api.UnallocatedResource
import com.atlassian.performance.tools.infrastructure.api.loadbalancer.LoadBalancer

class ProvisionedLoadBalancer
@Deprecated("Use ProvisionedLoadBalancer.Builder instead.")
constructor(
    val loadBalancer: LoadBalancer,
    val resource: Resource
) {

    object Defaults {
        val resource: Resource = UnallocatedResource()
    }

    class Builder(
        private val loadBalancer: LoadBalancer
    ) {
        private var resource: Resource = Defaults.resource

        fun resource(resource: Resource) = apply { this.resource = resource }

        @Suppress("DEPRECATION")
        fun build() = ProvisionedLoadBalancer(
            loadBalancer = loadBalancer,
            resource = resource
        )
    }
}
