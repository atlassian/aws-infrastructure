package com.atlassian.performance.tools.awsinfrastructure.api.elk

import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.model.*
import com.atlassian.performance.tools.aws.api.AwaitingEc2
import com.atlassian.performance.tools.aws.api.Investment
import com.atlassian.performance.tools.aws.api.SshKey

class AwsUbuntuKibana {

    fun provision(
        ec2: AmazonEC2,
        awaitingEc2: AwaitingEc2,
        key: SshKey,
        investment: Investment
    ): Kibana {
        val (ssh, _) = awaitingEc2.allocateInstance(
            investment = investment,
            key = key,
            vpcId = null,
            customizeLaunch = { launch ->
                launch
                    .withInstanceType(InstanceType.C5Xlarge)
                    .withSecurityGroupIds(
                        openPort(9200, "elasticsearch", ec2, awaitingEc2, investment).groupId,
                        openPort(9300, "elasticsearc-clustering", ec2, awaitingEc2, investment).groupId,
                        openPort(5601, "elk", ec2, awaitingEc2, investment).groupId
                    )
            }
        )
        key.file.facilitateSsh(ssh.host.ipAddress)
        val elasticsearch = ssh.newConnection().use { shell ->
            UbuntuElasticsearch().install(shell, 9200)
        }
        return ssh.newConnection().use { shell ->
            UbuntuKibana().install(
                shell = shell,
                port = 5601,
                elasticsearchHosts = listOf(elasticsearch)
            )
        }
    }

    private fun openPort(
        port: Int,
        label: String,
        ec2: AmazonEC2,
        awaitingEc2: AwaitingEc2,
        investment: Investment
    ): SecurityGroup {
        val securityGroup = awaitingEc2.allocateSecurityGroup(
            investment,
            CreateSecurityGroupRequest()
                .withGroupName("${investment.reuseKey()}-$label")
                .withDescription("Enables $label access")
                .withVpcId(null)
        )
        ec2.authorizeSecurityGroupIngress(
            AuthorizeSecurityGroupIngressRequest()
                .withGroupId(securityGroup.groupId)
                .withIpPermissions(
                    IpPermission()
                        .withIpProtocol("tcp")
                        .withFromPort(port)
                        .withToPort(port)
                        .withIpv4Ranges(
                            IpRange().withCidrIp("0.0.0.0/0")
                        )
                )
        )
        return securityGroup
    }
}