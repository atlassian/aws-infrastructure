package com.atlassian.performance.tools.awsinfrastructure.jira

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class JiraStatusTest {
    @Test
    fun shouldParseRunning() {
        val actual = JiraStatus.Parser.parseResponse("""{"state":"RUNNING"}""")

        assertThat(actual).isEqualTo(JiraStatus.RUNNING)
    }

    @Test
    fun shouldParseEmpty() {
        val actual = JiraStatus.Parser.parseResponse("")

        assertThat(actual).isNull()
    }

    @Test
    fun shouldParseBlank() {
        val actual = JiraStatus.Parser.parseResponse("   ")

        assertThat(actual).isNull()
    }

    @Test
    fun shouldParsePrettyPrinted() {
        val actual = JiraStatus.Parser.parseResponse(
            """
            {
                "state": "STARTING"
            }
            """.trimIndent()
        )

        assertThat(actual).isEqualTo(JiraStatus.STARTING)
    }

    @Test
    fun shouldParseUnknown() {
        val actual = JiraStatus.Parser.parseResponse("""{"state":"YOUR_ENGINE_IS_BURNING_OUT"}""")

        assertThat(actual).isNull()
    }

    @Test
    fun shouldParseGarbled() {
        val actual = JiraStatus.Parser.parseResponse("""<html>SURPRISE</html>""")

        assertThat(actual).isNull()
    }
}