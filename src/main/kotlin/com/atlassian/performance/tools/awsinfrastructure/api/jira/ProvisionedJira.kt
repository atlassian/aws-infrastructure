package com.atlassian.performance.tools.awsinfrastructure.api.jira

import com.atlassian.performance.tools.aws.api.Resource
import com.atlassian.performance.tools.aws.api.UnallocatedResource
import com.atlassian.performance.tools.awsinfrastructure.api.network.access.AccessProvider
import com.atlassian.performance.tools.awsinfrastructure.api.network.access.NoAccessProvider

class ProvisionedJira private constructor(
    val jira: Jira,
    val resource: Resource,
    val accessProvider: AccessProvider
) {
    object Defaults {
        val resource: Resource = UnallocatedResource()
        val accessProvider = NoAccessProvider()
    }

    @Deprecated(message = "Use ProvisionedJira.Builder instead.")
    constructor(
        jira: Jira,
        resource: Resource
    ) : this(
        jira = jira,
        resource = resource,
        accessProvider = Defaults.accessProvider
    )

    override fun toString(): String {
        return "ProvisionedJira(jira=$jira, resource=$resource)"
    }

    class Builder(
        private val jira: Jira
    ) {
        private var resource: Resource = Defaults.resource
        private var accessProvider: AccessProvider = Defaults.accessProvider

        fun resource(resource: Resource) = apply { this.resource = resource }
        fun accessProvider(accessProvider: AccessProvider) = apply { this.accessProvider = accessProvider }

        fun build() = ProvisionedJira(
            jira = jira,
            resource = resource,
            accessProvider = accessProvider
        )
    }
}