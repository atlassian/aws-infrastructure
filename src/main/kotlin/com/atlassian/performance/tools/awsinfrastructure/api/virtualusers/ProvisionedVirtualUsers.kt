package com.atlassian.performance.tools.awsinfrastructure.api.virtualusers

import com.atlassian.performance.tools.aws.api.Resource
import com.atlassian.performance.tools.aws.api.UnallocatedResource
import com.atlassian.performance.tools.infrastructure.api.virtualusers.VirtualUsers

class ProvisionedVirtualUsers<out T : VirtualUsers>
@Deprecated("Use ProvisionedVirtualUsers.Builder instead.")
constructor(
    val virtualUsers: T,
    val resource: Resource
) {
    object Defaults {
        val resource: Resource = UnallocatedResource()
    }

    override fun toString(): String {
        return "ProvisionedVirtualUsers(virtualUsers=$virtualUsers, resource=$resource)"
    }

    class Builder<out T : VirtualUsers>(
        private val virtualUsers: T
    ) {
        private var resource: Resource = Defaults.resource

        fun resource(resource: Resource) = apply { this.resource = resource }

        @Suppress("DEPRECATION")
        fun build() = ProvisionedVirtualUsers(
            virtualUsers = virtualUsers,
            resource = resource
        )
    }
}
