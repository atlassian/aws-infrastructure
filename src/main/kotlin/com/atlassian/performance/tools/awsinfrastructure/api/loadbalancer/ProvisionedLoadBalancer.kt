package com.atlassian.performance.tools.awsinfrastructure.api.loadbalancer

import com.atlassian.performance.tools.aws.api.Resource
import com.atlassian.performance.tools.aws.api.UnallocatedResource
import com.atlassian.performance.tools.awsinfrastructure.api.network.access.AccessProvider
import com.atlassian.performance.tools.awsinfrastructure.api.network.access.AccessRequester
import com.atlassian.performance.tools.awsinfrastructure.api.network.access.NoAccessProvider
import com.atlassian.performance.tools.awsinfrastructure.api.network.access.NoAccessRequester
import com.atlassian.performance.tools.infrastructure.api.loadbalancer.LoadBalancer

class ProvisionedLoadBalancer private constructor(
    val loadBalancer: LoadBalancer,
    val resource: Resource,
    val accessProvider: AccessProvider,
    val accessRequester: AccessRequester
) {
    class Builder(
        private val loadBalancer: LoadBalancer
    ) {
        private var resource: Resource = UnallocatedResource()
        private var accessProvider: AccessProvider = NoAccessProvider()
        private var accessRequester: AccessRequester = NoAccessRequester()

        fun resource(resource: Resource) = apply { this.resource = resource }
        fun accessProvider(accessProvider: AccessProvider) = apply { this.accessProvider = accessProvider }
        fun accessRequester(accessRequester: AccessRequester) = apply { this.accessRequester = accessRequester }

        fun build() = ProvisionedLoadBalancer(
            loadBalancer = loadBalancer,
            resource = resource,
            accessProvider = accessProvider,
            accessRequester = accessRequester
        )
    }
}
