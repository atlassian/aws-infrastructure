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
    override fun toString(): String {
        return "ProvisionedJira(jira=$jira, resource=$resource)"
    }

    class Builder(
        private val jira: Jira
    ) {
        private var resource: Resource = UnallocatedResource()
        private var accessProvider: AccessProvider = NoAccessProvider()

        fun resource(resource: Resource) = apply { this.resource = resource }
        fun accessProvider(accessProvider: AccessProvider) = apply { this.accessProvider = accessProvider }

        fun build() = ProvisionedJira(
            jira = jira,
            resource = resource,
            accessProvider = accessProvider
        )
    }
}
