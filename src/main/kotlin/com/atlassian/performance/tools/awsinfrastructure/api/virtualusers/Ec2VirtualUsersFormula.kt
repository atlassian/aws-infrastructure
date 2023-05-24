package com.atlassian.performance.tools.awsinfrastructure.api.virtualusers

import com.amazonaws.services.ec2.model.IamInstanceProfileSpecification
import com.amazonaws.services.ec2.model.InstanceType
import com.amazonaws.services.ec2.model.ShutdownBehavior
import com.atlassian.performance.tools.aws.api.*
import com.atlassian.performance.tools.awsinfrastructure.api.network.Network
import com.atlassian.performance.tools.awsinfrastructure.api.network.access.ForIpAccessRequester
import com.atlassian.performance.tools.awsinfrastructure.virtualusers.UbuntuVirtualUsersRuntime
import com.atlassian.performance.tools.infrastructure.api.browser.Browser
import com.atlassian.performance.tools.infrastructure.api.browser.Chrome
import com.atlassian.performance.tools.infrastructure.api.virtualusers.ResultsTransport
import com.atlassian.performance.tools.infrastructure.api.virtualusers.SshVirtualUsers
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.io.File
import java.util.concurrent.Future
import java.util.function.Supplier

class Ec2VirtualUsersFormula private constructor(
    private val nodeOrder: Int,
    private val shadowJar: File,
    private val browser: Browser,
    private val network: Network?,
    private val instanceType: InstanceType
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
        logger.debug("Setting up $name...")
        val sshKey = key.get()
        val (ssh, resource, instance) = allocateInstance(aws.awaitingEc2, roleProfile, sshKey, investment)
        val jarPath = UbuntuVirtualUsersRuntime().prepareForExecution(ssh, shadowJar, shadowJarTransport, browser)
        logger.debug("$name is ready to apply load")
        return ProvisionedVirtualUsers
            .Builder(
                SshVirtualUsers(
                    nodeOrder = nodeOrder,
                    name = name,
                    resultsTransport = resultsTransport,
                    jarName = jarPath,
                    ssh = ssh
                )
            )
            .resource(resource)
            .accessRequester(ForIpAccessRequester(Supplier { instance.publicIpAddress }))
            .build()
    }

    private fun allocateInstance(
        ec2: AwaitingEc2,
        roleProfile: String,
        key: SshKey,
        investment: Investment
    ): SshInstance = ec2.allocateInstance(
        investment = investment,
        key = key,
        vpcId = network?.vpc?.vpcId,
        customizeLaunch = { launch ->
            launch
                .withIamInstanceProfile(
                    IamInstanceProfileSpecification().withName(roleProfile)
                )
                .withInstanceInitiatedShutdownBehavior(ShutdownBehavior.Terminate)
                .withInstanceType(instanceType)
                .withSubnetId(network?.subnet?.subnetId)
        }
    )

    @Suppress("unused")
    class Builder(
        private var shadowJar: File
    ) {
        private var browser: Browser = Chrome()
        private var network: Network? = null
        private var nodeOrder: Int = 1
        private var instanceType: InstanceType = InstanceType.C59xlarge

        internal constructor(
            formula: Ec2VirtualUsersFormula
        ) : this(
            shadowJar = formula.shadowJar
        ) {
            browser = formula.browser
            network = formula.network
            nodeOrder = formula.nodeOrder
            instanceType = formula.instanceType
        }

        internal fun network(network: Network) = apply { this.network = network }
        fun instanceType(instanceType: InstanceType) = apply { this.instanceType = instanceType }
        fun browser(browser: Browser) = apply { this.browser = browser }
        fun nodeOrder(nodeOrder: Int) = apply { this.nodeOrder = nodeOrder }

        fun build(): VirtualUsersFormula<SshVirtualUsers> = Ec2VirtualUsersFormula(
            shadowJar = shadowJar,
            browser = browser,
            network = network,
            nodeOrder = nodeOrder,
            instanceType = instanceType
        )
    }
}
