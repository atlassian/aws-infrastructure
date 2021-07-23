package com.atlassian.performance.tools.awsinfrastructure.api.loadbalancer

import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.model.*
import com.atlassian.performance.tools.aws.api.*
import com.atlassian.performance.tools.infrastructure.api.jira.install.HttpNode
import com.atlassian.performance.tools.infrastructure.api.jira.install.TcpNode
import com.atlassian.performance.tools.infrastructure.api.network.HttpServerRoom
import java.util.concurrent.ConcurrentLinkedQueue

class Ec2ServerRoom(
    private val aws: Aws,
    private val investment: Investment
) : HttpServerRoom, AutoCloseable {

    private val balancerPort = 80
    private val resources = ConcurrentLinkedQueue<Resource>()

    override fun serveHttp(name: String): HttpNode {
        val httpAccess = httpAccess(investment, aws.ec2, aws.awaitingEc2, vpc)
        val (ssh, resource) = aws.awaitingEc2.allocateInstance(
            investment = investment,
            key = key,
            vpcId = vpc.vpcId,
            customizeLaunch = { launch ->
                launch
                    .withSecurityGroupIds(httpAccess.groupId)
                    .withSubnetId(subnet.subnetId)
                    .withInstanceType(InstanceType.M5Large)
            }
        )
        resources += resource
        key.file.facilitateSsh(ssh.host.ipAddress)
        return HttpNode(
            TcpNode(

            ),
            "/",
            false
        )
    }

    private fun httpAccess(
        investment: Investment,
        ec2: AmazonEC2,
        awaitingEc2: AwaitingEc2,
        vpc: Vpc
    ): SecurityGroup {
        val securityGroup = awaitingEc2.allocateSecurityGroup(
            investment,
            CreateSecurityGroupRequest()
                .withGroupName("${investment.reuseKey()}-HttpListener")
                .withDescription("Enables HTTP access")
                .withVpcId(vpc.vpcId)
        )
        ec2.authorizeSecurityGroupIngress(
            AuthorizeSecurityGroupIngressRequest()
                .withGroupId(securityGroup.groupId)
                .withIpPermissions(
                    IpPermission()
                        .withIpProtocol("tcp")
                        .withFromPort(balancerPort)
                        .withToPort(balancerPort)
                        .withIpv4Ranges(
                            IpRange().withCidrIp("0.0.0.0/0")
                        )
                )
        )
        return securityGroup
    }

    override fun close() {
        while (true) {
            resources
                .poll()
                ?.release()
                ?.get()
                ?: break
        }
    }
}
