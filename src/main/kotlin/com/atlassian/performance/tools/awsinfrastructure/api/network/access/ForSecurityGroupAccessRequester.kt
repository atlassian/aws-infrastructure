package com.atlassian.performance.tools.awsinfrastructure.api.network.access

import com.amazonaws.services.ec2.model.SecurityGroup

class ForSecurityGroupAccessRequester(
    private val securityGroupProvider: () -> SecurityGroup
) : AccessRequester {
    override fun requestAccess(
        accessProvider: AccessProvider
    ) = accessProvider
        .provideAccess(securityGroupProvider())
}
