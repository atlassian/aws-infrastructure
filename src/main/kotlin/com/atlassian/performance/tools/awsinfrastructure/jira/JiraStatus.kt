package com.atlassian.performance.tools.awsinfrastructure.jira

import org.apache.logging.log4j.LogManager
import java.io.StringReader
import javax.json.Json

/**
 * https://confluence.atlassian.com/jirakb/jira-status-endpoint-response-meanings-1116294680.html
 */
enum class JiraStatus {
    STARTING,
    STOPPING,
    FIRST_RUN,
    RUNNING,
    MAINTENANCE,
    ERROR;

    object Parser {

        private val LOG = LogManager.getLogger(this::class.java)

        fun parseResponse(response: String): JiraStatus? {
            val json = try {
                Json.createReader(StringReader(response)).read()
            } catch (e: Exception) {
                LOG.warn("That's not a JSON! $response", e)
                return null
            }
            return json.asJsonObject()
                ?.getString("state")
                ?.let { state -> JiraStatus.values().find { it.name == state } }
        }
    }
}
