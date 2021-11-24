package com.atlassian.performance.tools.awsinfrastructure.api.network.access

class NoAccessRequester : AccessRequester {
    override fun requestAccess(
        accessProvider: AccessProvider
    ) = true
}
