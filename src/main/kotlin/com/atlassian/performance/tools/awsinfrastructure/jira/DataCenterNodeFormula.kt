package com.atlassian.performance.tools.awsinfrastructure.jira

import com.atlassian.performance.tools.awsinfrastructure.api.jira.JiraSharedStorageResource
import com.atlassian.performance.tools.awsinfrastructure.api.jira.StartedNode
import com.atlassian.performance.tools.infrastructure.api.jira.SharedHome
import com.atlassian.performance.tools.ssh.api.Ssh
import java.util.EnumSet
import java.util.concurrent.Future

internal class DataCenterNodeFormula(
    private val nodeIndex: Int,
    private val sharedHome: Future<SharedHome>,
    private val base: NodeFormula,
    private val privateIpAddress: String,
    private val storeInS3: EnumSet<JiraSharedStorageResource> = EnumSet.of(
        JiraSharedStorageResource.ATTACHMENTS
    ),
    private val s3StorageBucketName: String? = null,
    private val awsRegion: String? = null
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

            if (s3StorageBucketName != null && storeInS3.isNotEmpty()) {
                val associations = storeInS3.map { element ->
                    """<association target="${element.name.toLowerCase()}" file-store="s3Bucket" />"""
                }
                val formattedRegion = awsRegion?.let { it1 -> convertRegionFormat(it1) }
                it.execute(
                    """
                    cat <<EOT > $jiraHome/filestore-config.xml
                    <?xml version="1.1" ?>
                    <filestore-config>
                      <filestores>
                        <s3-filestore id="s3Bucket">
                          <config>
                            <bucket-name>$s3StorageBucketName</bucket-name>
                            <region>$formattedRegion</region>
                          </config>
                        </s3-filestore>
                      </filestores>
                      <associations>
                        ${associations.joinToString("\n                    ")}
                      </associations>
                    </filestore-config>
                    EOT
                """.trimIndent()
                )
            }
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

    fun convertRegionFormat(region: String): String {
        return region.toLowerCase().replace('_', '-')
    }

    override fun toString() = "node formula #$nodeIndex"
}
