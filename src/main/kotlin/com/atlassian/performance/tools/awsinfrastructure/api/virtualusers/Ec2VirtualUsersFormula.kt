package com.atlassian.performance.tools.awsinfrastructure.api.virtualusers

import com.amazonaws.services.ec2.model.IamInstanceProfileSpecification
import com.amazonaws.services.ec2.model.InstanceType
import com.atlassian.performance.tools.aws.api.*
import com.atlassian.performance.tools.awsinfrastructure.virtualusers.UbuntuVirtualUsersRuntime
import com.atlassian.performance.tools.infrastructure.api.virtualusers.ResultsTransport
import com.atlassian.performance.tools.infrastructure.api.virtualusers.SshVirtualUsers
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.io.File
import java.util.concurrent.Future

class Ec2VirtualUsersFormula(
    private val nodeOrder: Int = 1,
    private val shadowJar: File
) : VirtualUsersFormula<SshVirtualUsers> {
    private val logger: Logger = LogManager.getLogger(this::class.java)

    private val name: String = "virtual-user-node-$nodeOrder"

    override fun provision(
        investment: Investment,
        shadowJarTransport: Storage,
        resultsTransport: ResultsTransport,
        key: Future<SshKey>,
        roleProfile: String,
        aws: Aws
    ): ProvisionedVirtualUsers<SshVirtualUsers> {
        logger.info("Setting up $name...")
        val sshKey = key.get()
        val (ssh, resource) = allocateInstance(aws.awaitingEc2, roleProfile, sshKey, investment)
        val jarPath = UbuntuVirtualUsersRuntime().prepareForExecution(ssh, shadowJar, shadowJarTransport)
        logger.info("$name is ready to apply load")
        return ProvisionedVirtualUsers(
            virtualUsers = SshVirtualUsers(
                nodeOrder = nodeOrder,
                name = name,
                resultsTransport = resultsTransport,
                jarName = jarPath,
                ssh = ssh
            ),
            resource = resource
        )
    }

    private fun allocateInstance(
        ec2: AwaitingEc2,
        roleProfile: String,
        key: SshKey,
        investment: Investment
    ): SshInstance = ec2.allocateInstance(
        investment = investment,
        key = key,
        vpcId = null,
        customizeLaunch = { launch ->
            launch
                .withIamInstanceProfile(
                    IamInstanceProfileSpecification().withName(roleProfile)
                )
                .withInstanceType(InstanceType.C48xlarge)
        }
    )
}