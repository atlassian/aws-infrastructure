package com.atlassian.performance.tools.awsinfrastructure.api.network.access

import com.amazonaws.services.ec2.model.SecurityGroup

class MultiAccessProvider(
    private val accessProviders: List<AccessProvider>
) : AccessProvider {
    override fun provideAccess(
        cidr: String
    ) = accessProviders
        .map { it.provideAccess(cidr) }
        .all { it }

    override fun provideAccess(
        securityGroup: SecurityGroup
    ) = accessProviders
        .map { it.provideAccess(securityGroup) }
        .all { it }
}
