package com.atlassian.performance.tools.awsinfrastructure.api

import com.atlassian.performance.tools.aws.api.Resource
import com.atlassian.performance.tools.aws.api.UnallocatedResource
import com.atlassian.performance.tools.infrastructure.api.virtualusers.VirtualUsers

class ProvisionedInfrastructure<out T : VirtualUsers>
@Deprecated("Use ProvisionedInfrastructure.Builder instead.")
constructor(
    val infrastructure: Infrastructure<T>,
    val resource: Resource
) {
    object Defaults {
        val resource: Resource = UnallocatedResource()
    }

    override fun toString(): String {
        return "ProvisionedInfrastructure(infrastructure=$infrastructure, resource=$resource)"
    }

    class Builder<out T : VirtualUsers>(
        private val infrastructure: Infrastructure<T>
    ) {
        private var resource: Resource = Defaults.resource

        fun resource(resource: Resource) = apply { this.resource = resource }

        @Suppress("DEPRECATION")
        fun build() = ProvisionedInfrastructure(
            infrastructure = infrastructure,
            resource = resource
        )
    }
}
