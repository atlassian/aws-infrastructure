package com.atlassian.performance.tools.awsinfrastructure.jira

import com.atlassian.performance.tools.ssh.api.Ssh

internal class DiagnosableStoppedNode(
    private val node: StoppedNode,
    private val nodeIdentifier: String
) : StoppedNode by node {
    override fun start(
        updateConfigurationFunction: List<(ssh: Ssh, unpackedProduct: String) -> Unit>
    ) = try {
        node.start(updateConfigurationFunction)
    } catch (e: Exception) {
        throw Exception("Failed to start $nodeIdentifier", e)
    }
}