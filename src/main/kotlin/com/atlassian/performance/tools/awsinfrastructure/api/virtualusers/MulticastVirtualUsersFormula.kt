package com.atlassian.performance.tools.awsinfrastructure.api.virtualusers

import com.amazonaws.services.ec2.model.InstanceType
import com.atlassian.performance.tools.aws.api.*
import com.atlassian.performance.tools.awsinfrastructure.api.network.Network
import com.atlassian.performance.tools.concurrency.api.submitWithLogContext
import com.atlassian.performance.tools.infrastructure.api.browser.Browser
import com.atlassian.performance.tools.infrastructure.api.browser.Chrome
import com.atlassian.performance.tools.infrastructure.api.splunk.DisabledSplunkForwarder
import com.atlassian.performance.tools.infrastructure.api.splunk.SplunkForwarder
import com.atlassian.performance.tools.infrastructure.api.virtualusers.MulticastVirtualUsers
import com.atlassian.performance.tools.infrastructure.api.virtualusers.ResultsTransport
import com.atlassian.performance.tools.infrastructure.api.virtualusers.SshVirtualUsers
import com.google.common.util.concurrent.ThreadFactoryBuilder
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicInteger

class MulticastVirtualUsersFormula private constructor(
    private val shadowJar: File,
    private val nodes: Int,
    private val splunkForwarder: SplunkForwarder,
    private val browser: Browser,
    private val network: Network?,
    private val instanceType: InstanceType
) : VirtualUsersFormula<MulticastVirtualUsers<SshVirtualUsers>> {
    private val logger: Logger = LogManager.getLogger(this::class.java)

    @Deprecated("Use MulticastVirtualUsersFormula.Builder")
    constructor(
        shadowJar: File,
        nodes: Int,
        splunkForwarder: SplunkForwarder,
        browser: Browser
    ) : this(
        shadowJar = shadowJar,
        nodes = nodes,
        splunkForwarder = splunkForwarder,
        browser = browser,
        network = null,
        instanceType = InstanceType.C48xlarge
    )

    @Deprecated("Use MulticastVirtualUsersFormula.Builder")
    constructor(
        shadowJar: File,
        nodes: Int
    ) : this(
        shadowJar = shadowJar,
        nodes = nodes,
        splunkForwarder = DisabledSplunkForwarder(),
        browser = Chrome(),
        network = null,
        instanceType = InstanceType.C48xlarge
    )

    override fun provision(
        investment: Investment,
        shadowJarTransport: Storage,
        resultsTransport: ResultsTransport,
        key: Future<SshKey>,
        roleProfile: String,
        aws: Aws
    ): ProvisionedVirtualUsers<MulticastVirtualUsers<SshVirtualUsers>> {
        val executor = Executors.newFixedThreadPool(
            nodes,
            ThreadFactoryBuilder()
                .setNameFormat("multicast-virtual-users-provisioning-thread-%d")
                .build()
        )

        logger.info("Provisioning $nodes virtual user node(s)...")
        val pendingVuNodes = AtomicInteger(nodes)
        val provisionedVirtualUsers = (1..nodes)
            .map { nodeOrder ->
                executor.submitWithLogContext("provision virtual users $nodeOrder") {
                    val provisionedVirtualUserNode = StackVirtualUsersFormula.Builder(
                        shadowJar = shadowJar
                    )
                        .nodeOrder(nodeOrder)
                        .splunkForwarder(splunkForwarder)
                        .browser(browser)
                        .instanceType(instanceType)
                        .also { if (network != null) it.network(network) }
                        .build()
                        .provision(
                            investment = investment.copy(reuseKey = { investment.reuseKey() + nodeOrder }),
                            shadowJarTransport = shadowJarTransport,
                            resultsTransport = resultsTransport,
                            key = key,
                            roleProfile = roleProfile,
                            aws = aws
                        )
                    logger.info("Virtual user nodes pending: " + pendingVuNodes.decrementAndGet())
                    provisionedVirtualUserNode
                }
            }
            .map { it.get() }

        executor.shutdownNow()

        return ProvisionedVirtualUsers(
            virtualUsers = MulticastVirtualUsers(provisionedVirtualUsers.map { it.virtualUsers }),
            resource = CompositeResource(provisionedVirtualUsers.map { it.resource })
        )
    }

    class Builder(
        private var nodes: Int,
        private var shadowJar: File
    ) {
        private var browser: Browser = Chrome()
        private var network: Network? = null
        private var splunkForwarder: SplunkForwarder = DisabledSplunkForwarder()
        private var instanceType: InstanceType = InstanceType.C48xlarge

        internal constructor(
            formula: MulticastVirtualUsersFormula
        ) : this(
            nodes = formula.nodes,
            shadowJar = formula.shadowJar
        ) {
            browser = formula.browser
            network = formula.network
            splunkForwarder = formula.splunkForwarder
            instanceType = formula.instanceType
        }

        fun browser(browser: Browser) = apply { this.browser = browser }

        /**
         * Connects all VU nodes.
         */
        fun network(network: Network) = apply { this.network = network }
        fun splunkForwarder(splunkForwarder: SplunkForwarder) = apply { this.splunkForwarder = splunkForwarder }
        fun instanceType(instanceType: InstanceType): MulticastVirtualUsersFormula.Builder = apply { this.instanceType = instanceType }

        fun build(): VirtualUsersFormula<MulticastVirtualUsers<SshVirtualUsers>> = MulticastVirtualUsersFormula(
            nodes = nodes,
            shadowJar = shadowJar,
            splunkForwarder = splunkForwarder,
            browser = browser,
            network = network,
            instanceType = instanceType
        )
    }
}
