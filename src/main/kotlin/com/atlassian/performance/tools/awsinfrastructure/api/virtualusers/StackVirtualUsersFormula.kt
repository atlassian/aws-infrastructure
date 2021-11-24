package com.atlassian.performance.tools.awsinfrastructure.api.virtualusers

import com.amazonaws.services.cloudformation.model.Parameter
import com.amazonaws.services.ec2.model.InstanceType
import com.atlassian.performance.tools.aws.api.*
import com.atlassian.performance.tools.awsinfrastructure.AmiNameResolver
import com.atlassian.performance.tools.awsinfrastructure.InstanceAddressSelector
import com.atlassian.performance.tools.awsinfrastructure.InstanceFilters
import com.atlassian.performance.tools.awsinfrastructure.api.network.Network
import com.atlassian.performance.tools.awsinfrastructure.api.network.NetworkFormula
import com.atlassian.performance.tools.awsinfrastructure.virtualusers.UbuntuVirtualUsersRuntime
import com.atlassian.performance.tools.infrastructure.api.browser.Browser
import com.atlassian.performance.tools.infrastructure.api.browser.Chrome
import com.atlassian.performance.tools.infrastructure.api.splunk.DisabledSplunkForwarder
import com.atlassian.performance.tools.infrastructure.api.splunk.SplunkForwarder
import com.atlassian.performance.tools.infrastructure.api.virtualusers.ResultsTransport
import com.atlassian.performance.tools.infrastructure.api.virtualusers.SshVirtualUsers
import com.atlassian.performance.tools.io.api.readResourceText
import com.atlassian.performance.tools.ssh.api.Ssh
import com.atlassian.performance.tools.ssh.api.SshHost
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.io.File
import java.time.Duration
import java.util.concurrent.Future

/**
 * The EC2 instances provisioned with this class will have 'instance initiated shutdown' parameter set to 'terminate'.
 *
 * @param [splunkForwarder] Forwards logs from `/home/ubuntu/splunkforward`.
 */
class StackVirtualUsersFormula private constructor(
    private val nodeOrder: Int = 1,
    private val shadowJar: File,
    private val splunkForwarder: SplunkForwarder,
    private val browser: Browser,
    private val stackCreationTimeout: Duration,
    private val overriddenNetwork: Network? = null,
    private val instanceType: InstanceType,
    private val sshCidrIp: String
) : VirtualUsersFormula<SshVirtualUsers> {
    private val logger: Logger = LogManager.getLogger(this::class.java)

    private val name: String = "virtual-user-node-$nodeOrder"

    @Deprecated(message = "Use StackVirtualUsersFormula.Builder instead.")
    constructor(
        nodeOrder: Int = 1,
        shadowJar: File,
        splunkForwarder: SplunkForwarder,
        browser: Browser
    ) : this(
        nodeOrder = nodeOrder,
        shadowJar = shadowJar,
        splunkForwarder = splunkForwarder,
        browser = browser,
        stackCreationTimeout = Duration.ofMinutes(30),
        instanceType = InstanceType.C48xlarge,
        sshCidrIp = ""
    )

    @Deprecated(message = "Use StackVirtualUsersFormula.Builder instead.")
    constructor(
        shadowJar: File
    ) : this(
        nodeOrder = 1,
        shadowJar = shadowJar,
        splunkForwarder = DisabledSplunkForwarder(),
        browser = Chrome(),
        stackCreationTimeout = Duration.ofMinutes(30),
        instanceType = InstanceType.C48xlarge,
        sshCidrIp = ""
    )

    @Deprecated(message = "Use StackVirtualUsersFormula.Builder instead.")
    constructor(
        shadowJar: File,
        splunkForwarder: SplunkForwarder
    ) : this(
        nodeOrder = 1,
        shadowJar = shadowJar,
        splunkForwarder = splunkForwarder,
        browser = Chrome(),
        stackCreationTimeout = Duration.ofMinutes(30),
        instanceType = InstanceType.C48xlarge,
        sshCidrIp = ""
    )

    override fun provision(
        investment: Investment,
        shadowJarTransport: Storage,
        resultsTransport: ResultsTransport,
        key: Future<SshKey>,
        roleProfile: String,
        aws: Aws
    ): ProvisionedVirtualUsers<SshVirtualUsers> {
        logger.debug("Setting up $name...")
        val network = overriddenNetwork ?: NetworkFormula(investment, aws).provision()
        val virtualUsersStack = StackFormula(
            investment = investment,
            cloudformationTemplate = readResourceText("aws/virtual-users.yaml"),
            parameters = listOf(
                Parameter()
                    .withParameterKey("KeyName")
                    .withParameterValue(key.get().remote.name),
                Parameter()
                    .withParameterKey("InstanceProfile")
                    .withParameterValue(roleProfile),
                Parameter()
                    .withParameterKey("Ami")
                    .withParameterValue(AmiNameResolver.vuAmi(aws)),
                Parameter()
                    .withParameterKey("Vpc")
                    .withParameterValue(network.vpc.vpcId),
                Parameter()
                    .withParameterKey("Subnet")
                    .withParameterValue(network.subnet.subnetId),
                Parameter()
                    .withParameterKey("InstanceType")
                    .withParameterValue(instanceType.toString()),
                Parameter()
                    .withParameterKey("SSHCidrIp")
                    .withParameterValue(sshCidrIp)
            ),
            aws = aws,
            pollingTimeout = stackCreationTimeout
        ).provision()

        val virtualUsersMachine = InstanceFilters().vuNodes(virtualUsersStack.listMachines())
        val virtualUsersIp = InstanceAddressSelector.getReachableIpAddress(virtualUsersMachine)
        val virtualUsersHost = SshHost(virtualUsersIp, "ubuntu", key.get().file.path)
        val virtualUsersSsh = Ssh(virtualUsersHost, connectivityPatience = 4)

        key.get().file.facilitateSsh(virtualUsersIp)

        val jarPath = UbuntuVirtualUsersRuntime().prepareForExecution(
            virtualUsersSsh,
            shadowJar,
            shadowJarTransport,
            browser
        )
        virtualUsersSsh.newConnection().use {
            it.execute("mkdir splunkforward")
            splunkForwarder.run(it, name, logsPath = "/home/ubuntu/splunkforward/")
        }
        logger.debug("$name is ready to apply load")
        return ProvisionedVirtualUsers
            .Builder(
                SshVirtualUsers(
                    nodeOrder = nodeOrder,
                    name = name,
                    resultsTransport = resultsTransport,
                    jarName = jarPath,
                    ssh = virtualUsersSsh
                )
            )
            .resource(virtualUsersStack)
            .build()
    }

    class Builder(
        private val shadowJar: File
    ) {
        private var nodeOrder: Int = 1
        private var splunkForwarder: SplunkForwarder = DisabledSplunkForwarder()
        private var browser: Browser = Chrome()
        private var stackCreationTimeout: Duration = Duration.ofMinutes(30)
        private var network: Network? = null
        private var instanceType: InstanceType = InstanceType.C48xlarge
        private var sshCidrIp: String = ""

        internal constructor(
            formula: StackVirtualUsersFormula
        ) : this(
            shadowJar = formula.shadowJar
        ) {
            nodeOrder = formula.nodeOrder
            splunkForwarder = formula.splunkForwarder
            browser = formula.browser
            stackCreationTimeout = formula.stackCreationTimeout
            network = formula.overriddenNetwork
            instanceType = formula.instanceType
            sshCidrIp = formula.sshCidrIp
        }

        fun nodeOrder(nodeOrder: Int): Builder = apply { this.nodeOrder = nodeOrder }
        fun splunkForwarder(splunkForwarder: SplunkForwarder): Builder = apply { this.splunkForwarder = splunkForwarder }
        fun browser(browser: Browser): Builder = apply { this.browser = browser }
        fun stackCreationTimeout(stackCreationTimeout: Duration): Builder = apply { this.stackCreationTimeout = stackCreationTimeout }
        fun instanceType(instanceType: InstanceType): Builder = apply { this.instanceType = instanceType }
        fun sshCidrIp(sshCidrIp: String): Builder = apply { this.sshCidrIp = sshCidrIp }
        internal fun network(network: Network): Builder = apply { this.network = network }

        fun build(): StackVirtualUsersFormula = StackVirtualUsersFormula(
            nodeOrder = nodeOrder,
            shadowJar = shadowJar,
            splunkForwarder = splunkForwarder,
            browser = browser,
            stackCreationTimeout = stackCreationTimeout,
            overriddenNetwork = network,
            instanceType = instanceType,
            sshCidrIp = sshCidrIp
        )
    }
}
