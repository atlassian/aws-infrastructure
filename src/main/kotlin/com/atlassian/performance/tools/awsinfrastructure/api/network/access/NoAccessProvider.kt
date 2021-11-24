package com.atlassian.performance.tools.awsinfrastructure.api.network.access

import com.amazonaws.services.ec2.model.SecurityGroup

class NoAccessProvider : AccessProvider {
    override fun provideAccess(
        cidr: String
    ) = true

    override fun provideAccess(
        securityGroup: SecurityGroup
    ) = true
}
