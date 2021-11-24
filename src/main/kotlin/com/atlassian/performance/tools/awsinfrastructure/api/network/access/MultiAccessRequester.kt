package com.atlassian.performance.tools.awsinfrastructure.api.network.access

class MultiAccessRequester(
    private val requesters: List<AccessRequester>
) : AccessRequester {
    override fun requestAccess(
        accessProvider: AccessProvider
    ) = requesters
        .map { it.requestAccess(accessProvider) }
        .all { it }
}
