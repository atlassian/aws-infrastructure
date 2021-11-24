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
    object Defaults {
        val resource: Resource = UnallocatedResource()
        val accessProvider: AccessProvider = NoAccessProvider()
    }

    @Deprecated("Use ProvisionedInfrastructure.Builder instead.")
    constructor(
        infrastructure: Infrastructure<T>,
        resource: Resource
    ) : this(
        infrastructure = infrastructure,
        resource = resource,
        accessProvider = Defaults.accessProvider
    )

    override fun toString(): String {
        return "ProvisionedInfrastructure(infrastructure=$infrastructure, resource=$resource)"
    }

    class Builder<out T : VirtualUsers>(
        private val infrastructure: Infrastructure<T>
    ) {
        private var resource: Resource = Defaults.resource
        private var accessProvider: AccessProvider = Defaults.accessProvider

        fun resource(resource: Resource) = apply { this.resource = resource }
        fun accessProvider(accessProvider: AccessProvider) = apply { this.accessProvider = accessProvider }

        fun build() = ProvisionedInfrastructure(
            infrastructure = infrastructure,
            resource = resource,
            accessProvider = accessProvider
        )
    }
}
