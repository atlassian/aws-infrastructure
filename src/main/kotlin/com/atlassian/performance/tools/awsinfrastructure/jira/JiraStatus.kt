package com.atlassian.performance.tools.awsinfrastructure.jira

import java.io.StringReader
import javax.json.Json
import kotlin.streams.asSequence

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

        fun parseResponse(response: String): JiraStatus? {
            return Json.createParser(StringReader(response)).use { jsonParser ->
                jsonParser
                    .valueStream
                    .asSequence()
                    .firstOrNull()
                    ?.asJsonObject()
                    ?.getString("state")
                    ?.let { state -> JiraStatus.values().find { it.name == state } }
            }
        }
    }
}
