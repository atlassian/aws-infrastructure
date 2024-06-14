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
            val state = try {
                Json.createReader(StringReader(response))
                    .read()
                    .asJsonObject()
                    .getString("state")
            } catch (e: Exception) {
                LOG.warn("Invalid JSON state: '$response'", e)
                return null
            }
            return JiraStatus.values().find { it.name == state }
        }
    }
}
