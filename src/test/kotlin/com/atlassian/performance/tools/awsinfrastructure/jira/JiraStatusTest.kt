package com.atlassian.performance.tools.awsinfrastructure.jira

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class JiraStatusTest {
    @Test
    fun shouldParseRunning() {
        val actual = JiraStatus.Parser.parseResponse("""{"state":"RUNNING"}""")

        assertThat(actual).isEqualTo(JiraStatus.RUNNING)
    }
}