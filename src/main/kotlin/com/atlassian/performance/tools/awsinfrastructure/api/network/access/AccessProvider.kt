package com.atlassian.performance.tools.awsinfrastructure.api.network.access

import com.amazonaws.services.ec2.model.SecurityGroup

/**
 * Used to grant access to a network resource, e.g. web application.
 * Introduced to allow restriction of anonymous access.
 */
interface AccessProvider {

    /**
     * @param cidr [Classless Inter-Domain Routing](https://en.wikipedia.org/wiki/Classless_Inter-Domain_Routing) format of IP and network mask
     * @return true if provide access was successful, false otherwise
     */
    fun provideAccess(cidr: String): Boolean

    /**
     * @param securityGroup group to which the access should be granted
     * @return true if provide access was successful, false otherwise
     */
    fun provideAccess(securityGroup: SecurityGroup): Boolean
}
