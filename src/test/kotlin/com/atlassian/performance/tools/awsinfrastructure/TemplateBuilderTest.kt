package com.atlassian.performance.tools.awsinfrastructure

import org.hamcrest.Matchers.containsString
import org.junit.Assert.assertThat
import org.junit.Test

class TemplateBuilderTest {
    @Test
    fun shouldSetNodesCount() {
        val nodesCount = 4

        val changedTemplate = TemplateBuilder("2-nodes-dc.yaml").setNodesCount(nodesCount).build()

        for (nodeNumber in 1..nodesCount) {
            assertThat(changedTemplate, containsString("jira$nodeNumber"))
        }
    }
    @Test
    fun shouldPreserveEmptySingleQuotes() {
        val changedTemplate = TemplateBuilder("object-storage.yaml").build()

        assertThat(changedTemplate, containsString("''"))
    }
}
