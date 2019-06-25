package com.atlassian.performance.tools.awsinfrastructure.jira

import com.atlassian.performance.tools.awsinfrastructure.api.jira.StartedNode
import com.atlassian.performance.tools.infrastructure.api.jira.SharedHome
import com.atlassian.performance.tools.ssh.api.Ssh
import java.util.concurrent.Future

internal class DataCenterNodeFormula(
    private val nodeIndex: Int,
    private val sharedHome: Future<SharedHome>,
    private val base: NodeFormula,
    private val privateIpAddress: String
) : NodeFormula by base {

    override fun provision(): StoppedNode {

        val provisionedNode = base.provision()
        val localSharedHome = sharedHome.get().localSharedHome

        provisionedNode.ssh.newConnection().use {
            sharedHome.get().mount(it)
            val jiraHome = provisionedNode.jiraHome

            it.execute("echo ehcache.listener.hostName = $privateIpAddress >> $jiraHome/cluster.properties")
            it.execute("echo ehcache.object.port = 40011 >> $jiraHome/cluster.properties")
            it.execute("echo jira.node.id = node$nodeIndex >> $jiraHome/cluster.properties")
            it.execute("echo jira.shared.home = `realpath $localSharedHome` >> $jiraHome/cluster.properties")
        }

        return object : StoppedNode by provisionedNode {
            override fun start(updateConfigurationFunction: List<(ssh: Ssh, unpackedProduct: String) -> Unit>): StartedNode {
                return provisionedNode.start(updateConfigurationFunction).copy(
                    name = name,
                    analyticLogs = localSharedHome
                )
            }

            override fun toString() = "node #$nodeIndex"
        }
    }

    override fun toString() = "node formula #$nodeIndex"
}