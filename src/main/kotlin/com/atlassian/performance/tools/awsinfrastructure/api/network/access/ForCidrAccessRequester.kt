package com.atlassian.performance.tools.awsinfrastructure.api.network.access

import java.util.function.Supplier

class ForCidrAccessRequester(
    private val cidrProvider: Supplier<String>
) : AccessRequester {
    override fun requestAccess(
        accessProvider: AccessProvider
    ) = accessProvider
        .provideAccess(cidrProvider.get())
}
