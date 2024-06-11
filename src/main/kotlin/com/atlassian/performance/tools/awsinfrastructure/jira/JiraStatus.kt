package com.atlassian.performance.tools.awsinfrastructure.jira

import java.io.StringReader
import javax.json.Json


enum class JiraStatus {
    STARTING,
    RUNNING;

    object Parser {

        fun parseResponse(response: String): JiraStatus? {
            return Json.createParser(StringReader(response)).use { jsonParser ->
                if (jsonParser.hasNext()) {
                    jsonParser.next()
                    JiraStatus.valueOf(jsonParser.`object`.getString("state"))
                } else {
                    null
                }
            }
        }
    }

}
