package com.atlassian.performance.tools.awsinfrastructure.jira

import java.io.StringReader
import javax.json.Json
import kotlin.streams.asSequence


enum class JiraStatus {
    STARTING,
    RUNNING;

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
