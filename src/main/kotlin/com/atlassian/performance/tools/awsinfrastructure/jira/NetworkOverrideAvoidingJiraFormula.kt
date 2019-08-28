package com.atlassian.performance.tools.awsinfrastructure.jira

import com.atlassian.performance.tools.awsinfrastructure.api.jira.JiraFormula

class NetworkOverrideAvoidingJiraFormula(
    private val base: JiraFormula
) : JiraFormula by base