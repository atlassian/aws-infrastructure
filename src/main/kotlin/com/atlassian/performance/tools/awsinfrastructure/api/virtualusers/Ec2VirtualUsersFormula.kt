package com.atlassian.performance.tools.awsinfrastructure.api.virtualusers

import com.amazonaws.services.ec2.model.IamInstanceProfileSpecification
import com.amazonaws.services.ec2.model.InstanceType
import com.atlassian.performance.tools.aws.api.*
import com.atlassian.performance.tools.awsinfrastructure.api.network.Network
import com.atlassian.performance.tools.awsinfrastructure.virtualusers.UbuntuVirtualUsersRuntime
import com.atlassian.performance.tools.infrastructure.api.browser.Browser
import com.atlassian.performance.tools.infrastructure.api.browser.Chrome
import com.atlassian.performance.tools.infrastructure.api.virtualusers.ResultsTransport
import com.atlassian.performance.tools.infrastructure.api.virtualusers.SshVirtualUsers
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.io.File
import java.util.concurrent.Future

class Ec2VirtualUsersFormula private constructor(
    private val nodeOrder: Int,
    private val shadowJar: File,
    private val browser: Browser,
    private val network: Network?,
    private val instanceType: InstanceType
) : VirtualUsersFormula<SshVirtualUsers> {

    @Deprecated("Use Ec2VirtualUsersFormula.Builder")
    constructor(
        nodeOrder: Int = 1,
        shadowJar: File,
        browser: Browser
    ) : this(
        nodeOrder = nodeOrder,
        shadowJar = shadowJar,
        browser = browser,
        network = null,
        instanceType = InstanceType.C48xlarge
    )

    @Deprecated("Use Ec2VirtualUsersFormula.Builder")
    constructor(
        shadowJar: File
    ) : this(
        nodeOrder = 1,
        shadowJar = shadowJar,
        browser = Chrome(),
        network = null,
        instanceType = InstanceType.C48xlarge
    )

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
        val (ssh, resource) = allocateInstance(aws.awaitingEc2, roleProfile, sshKey, investment)
        val jarPath = UbuntuVirtualUsersRuntime().prepareForExecution(ssh, shadowJar, shadowJarTransport, browser)
        logger.debug("$name is ready to apply load")
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
        vpcId = network?.vpc?.vpcId,
        customizeLaunch = { launch ->
            launch
                .withIamInstanceProfile(
                    IamInstanceProfileSpecification().withName(roleProfile)
                )
                .withInstanceType(instanceType)
                .withSubnetId(network?.subnet?.subnetId)
        }
    )

    class Builder(
        private var shadowJar: File
    ) {
        private var browser: Browser = Chrome()
        private var network: Network? = null
        private var nodeOrder: Int = 1
        private var instanceType: InstanceType = InstanceType.C48xlarge

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
        @Deprecated("This method sets instanceType, not network." +
            "It was introduced by mistake and it'll be removed in the next MAJOR release." +
            "Please use `instanceType` method instead.")
        fun network(instanceType: InstanceType) = apply { this.instanceType = instanceType }
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
