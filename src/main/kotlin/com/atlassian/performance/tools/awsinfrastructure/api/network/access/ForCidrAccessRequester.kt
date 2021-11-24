package com.atlassian.performance.tools.awsinfrastructure.api.network.access

class ForCidrAccessRequester(
    private val cidrProvider: () -> String
) : AccessRequester {
    override fun requestAccess(
        accessProvider: AccessProvider
    ) = accessProvider
        .provideAccess(cidrProvider())
}
