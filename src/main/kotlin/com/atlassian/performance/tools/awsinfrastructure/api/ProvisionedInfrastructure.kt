package com.atlassian.performance.tools.awsinfrastructure.api

import com.atlassian.performance.tools.aws.api.Resource
import com.atlassian.performance.tools.aws.api.UnallocatedResource
import com.atlassian.performance.tools.awsinfrastructure.api.network.access.AccessProvider
import com.atlassian.performance.tools.awsinfrastructure.api.network.access.NoAccessProvider
import com.atlassian.performance.tools.infrastructure.api.virtualusers.VirtualUsers

class ProvisionedInfrastructure<out T : VirtualUsers> private constructor(
    val infrastructure: Infrastructure<T>,
    val resource: Resource,
    val accessProvider: AccessProvider
) {
    override fun toString(): String {
        return "ProvisionedInfrastructure(infrastructure=$infrastructure, resource=$resource)"
    }

    class Builder<out T : VirtualUsers>(
        private val infrastructure: Infrastructure<T>
    ) {
        private var resource: Resource = UnallocatedResource()
        private var accessProvider: AccessProvider = NoAccessProvider()

        fun resource(resource: Resource) = apply { this.resource = resource }
        fun accessProvider(accessProvider: AccessProvider) = apply { this.accessProvider = accessProvider }

        fun build() = ProvisionedInfrastructure(
            infrastructure = infrastructure,
            resource = resource,
            accessProvider = accessProvider
        )
    }
}
