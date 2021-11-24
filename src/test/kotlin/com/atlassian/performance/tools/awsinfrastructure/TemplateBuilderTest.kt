package com.atlassian.performance.tools.awsinfrastructure

import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.junit.Assert.assertThat
import org.junit.Test

class TemplateBuilderTest {

    @Test
    fun shouldAddSecurityGroupIngress() {
        val port = 9997
        val baseTemplate = "single-node.yaml"
        val originalTemplate = TemplateBuilder(baseTemplate).build()

        val changedTemplate = TemplateBuilder(baseTemplate)
            .let {
                it.addSecurityGroupIngress(
                    newResourceKey = "TestIngress",
                    groupId = it.getResourceReference("JiraNodeSecurityGroup"),
                    ports = (port..port)
                )
            }
            .build()

        val expectedOutputs = listOf("FromPort: $port", "ToPort: $port")
        for (expectedOutput in expectedOutputs) {
            assertThat(originalTemplate, not(containsString(expectedOutput)))
            assertThat(changedTemplate, containsString(expectedOutput))
        }
    }

    @Test
    fun shouldSetNodesCount() {
        val nodesCount = 4

        val changedTemplate = TemplateBuilder("2-nodes-dc.yaml").setNodesCount(nodesCount).build()

        for (nodeNumber in 1..nodesCount) {
            assertThat(changedTemplate, containsString("jira$nodeNumber"))
        }
    }


}