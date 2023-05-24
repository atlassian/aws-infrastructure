package com.atlassian.performance.tools.awsinfrastructure.api.network.access

import com.amazonaws.services.ec2.model.SecurityGroup
import java.util.function.Supplier

class ForSecurityGroupAccessRequester(
    private val securityGroupProvider: Supplier<SecurityGroup>
) : AccessRequester {
    override fun requestAccess(
        accessProvider: AccessProvider
    ) = accessProvider
        .provideAccess(securityGroupProvider.get())
}
