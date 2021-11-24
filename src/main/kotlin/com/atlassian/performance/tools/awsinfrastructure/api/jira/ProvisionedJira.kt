package com.atlassian.performance.tools.awsinfrastructure.api.jira

import com.atlassian.performance.tools.aws.api.Resource
import com.atlassian.performance.tools.aws.api.UnallocatedResource

class ProvisionedJira
@Deprecated(message = "Use ProvisionedJira.Builder instead.")
constructor(
    val jira: Jira,
    val resource: Resource
) {
    object Defaults {
        val resource: Resource = UnallocatedResource()
    }

    override fun toString(): String {
        return "ProvisionedJira(jira=$jira, resource=$resource)"
    }

    class Builder(
        private val jira: Jira
    ) {
        private var resource: Resource = Defaults.resource

        fun resource(resource: Resource) = apply { this.resource = resource }

        @Suppress("DEPRECATION")
        fun build() = ProvisionedJira(
            jira = jira,
            resource = resource
        )
    }
}
