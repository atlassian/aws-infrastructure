package com.atlassian.performance.tools.awsinfrastructure.api.virtualusers

import com.atlassian.performance.tools.aws.api.Aws
import com.atlassian.performance.tools.aws.api.Investment
import com.atlassian.performance.tools.aws.api.SshKey
import com.atlassian.performance.tools.aws.api.Storage
import com.atlassian.performance.tools.awsinfrastructure.api.network.Network
import com.atlassian.performance.tools.infrastructure.api.browser.Browser
import com.atlassian.performance.tools.infrastructure.api.browser.Chrome
import com.atlassian.performance.tools.infrastructure.api.splunk.DisabledSplunkForwarder
import com.atlassian.performance.tools.infrastructure.api.splunk.SplunkForwarder
import com.atlassian.performance.tools.infrastructure.api.virtualusers.MulticastVirtualUsers
import com.atlassian.performance.tools.infrastructure.api.virtualusers.ResultsTransport
import com.atlassian.performance.tools.infrastructure.api.virtualusers.SshVirtualUsers
import java.io.File
import java.util.concurrent.Future

class MulticastVirtualUsersFormula private constructor(
    private val shadowJar: File,
    private val nodes: Int,
    private val splunkForwarder: SplunkForwarder,
    private val browser: Browser,
    private val network: Network?
) : VirtualUsersFormula<MulticastVirtualUsers<SshVirtualUsers>> {

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
        network = null
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
        network = null
    )

    override fun provision(
        investment: Investment,
        shadowJarTransport: Storage,
        resultsTransport: ResultsTransport,
        key: Future<SshKey>,
        roleProfile: String,
        aws: Aws
    ): ProvisionedVirtualUsers<MulticastVirtualUsers<SshVirtualUsers>> = MultiVirtualUsersFormula(
        base = StackVirtualUsersFormula.Builder(
            shadowJar = shadowJar
        )
            .nodeOrder(1)
            .splunkForwarder(splunkForwarder)
            .browser(browser)
            .also { if (network != null) it.network(network) }
            .build(),
        nodeCount = nodes
    ).provision(
        investment,
        shadowJarTransport,
        resultsTransport,
        key,
        roleProfile,
        aws
    )

    class Builder(
        private var nodes: Int,
        private var shadowJar: File
    ) {
        private var browser: Browser = Chrome()
        private var network: Network? = null
        private var splunkForwarder: SplunkForwarder = DisabledSplunkForwarder()

        internal constructor(
            formula: MulticastVirtualUsersFormula
        ) : this(
            nodes = formula.nodes,
            shadowJar = formula.shadowJar
        ) {
            browser = formula.browser
            network = formula.network
            splunkForwarder = formula.splunkForwarder
        }

        fun browser(browser: Browser) = apply { this.browser = browser }

        /**
         * Connects all VU nodes.
         */
        fun network(network: Network) = apply { this.network = network }

        fun splunkForwarder(splunkForwarder: SplunkForwarder) = apply { this.splunkForwarder = splunkForwarder }

        fun build(): VirtualUsersFormula<MulticastVirtualUsers<SshVirtualUsers>> = MulticastVirtualUsersFormula(
            nodes = nodes,
            shadowJar = shadowJar,
            splunkForwarder = splunkForwarder,
            browser = browser,
            network = network
        )
    }
}
