package com.atlassian.performance.tools.awsinfrastructure.jira

internal interface NodeFormula {
    val name: String

    fun provision(): StoppedNode
}

