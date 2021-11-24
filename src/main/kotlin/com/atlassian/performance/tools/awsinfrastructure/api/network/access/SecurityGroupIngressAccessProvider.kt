package com.atlassian.performance.tools.awsinfrastructure.api.network.access

import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.model.*

class SecurityGroupIngressAccessProvider private constructor(
    private val ec2: AmazonEC2,
    private val securityGroup: SecurityGroup,
    private val portRange: IntRange,
    private val protocol: String
) : AccessProvider {

    /**
     * No support for IPv6 yet.
     */
    override fun provideAccess(
        cidr: String
    ) = provideAccess(
        baseIpPermissions()
            .withIpv4Ranges(IpRange().withCidrIp(cidr))
    )

    override fun provideAccess(
        securityGroup: SecurityGroup
    ) = provideAccess(
        baseIpPermissions()
            .withUserIdGroupPairs(
                UserIdGroupPair()
                    .withUserId(securityGroup.ownerId)
                    .withGroupId(securityGroup.groupId)
            )
    )

    private fun baseIpPermissions() = IpPermission()
        .withIpProtocol("tcp")
        .withFromPort(portRange.first)
        .withToPort(portRange.last)
        .withIpProtocol(protocol)

    private fun provideAccess(
        ipPermissions: IpPermission
    ) = ec2.authorizeSecurityGroupIngress(
        AuthorizeSecurityGroupIngressRequest()
            .withGroupId(securityGroup.groupId)
            .withIpPermissions(ipPermissions)
    ).sdkHttpMetadata
        .httpStatusCode / 100 == 2

    class Builder(
        private val ec2: AmazonEC2,
        private val securityGroup: SecurityGroup,
        private val portRange: IntRange
    ) {
        private var protocol: String = "tcp"

        fun protocol(protocol: String) = apply { this.protocol = protocol }

        fun build() = SecurityGroupIngressAccessProvider(
            ec2 = ec2,
            securityGroup = securityGroup,
            portRange = portRange,
            protocol = protocol
        )
    }
}
