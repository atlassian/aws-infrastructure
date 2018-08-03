package com.atlassian.performance.tools.awsinfrastructure.jira

internal class DiagnosableNodeFormula(
    private val delegate: NodeFormula
) : NodeFormula by delegate {

    override fun provision(): StoppedNode {
        try {
            return delegate.provision()
        } catch (e: Exception) {
            throw Exception("Failed to provision ${delegate.name}", e)
        }
    }
}