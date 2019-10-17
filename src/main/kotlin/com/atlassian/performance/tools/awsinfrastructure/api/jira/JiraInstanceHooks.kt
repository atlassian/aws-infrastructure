package com.atlassian.performance.tools.awsinfrastructure.api.jira

import com.atlassian.performance.tools.infrastructure.api.jira.hook.JiraNodeHooks
import java.net.URI
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue

class JiraInstanceHooks {

    private val preProvisionHooks: Queue<PreProvisionHook> = ConcurrentLinkedQueue()
    private val instanceUriHooks: Queue<InstanceUriHook> = ConcurrentLinkedQueue()

    fun hook(hook: PreProvisionHook) {
        preProvisionHooks.add(hook)
    }

    internal fun runPreProvisionHooks(nodeHooks: List<JiraNodeHooks>) {
        while (true) {
            preProvisionHooks
                .poll()
                ?.run(this, nodeHooks)
                ?: break
        }
    }

    fun hook(hook: InstanceUriHook) {
        instanceUriHooks.add(hook)
    }

    internal fun runInstanceUriHooks(instance: URI, nodeHooks: List<JiraNodeHooks>) {
        while (true) {
            instanceUriHooks
                .poll()
                ?.run(instance, this, nodeHooks)
                ?: break
        }
    }
}

interface PreProvisionHook {

    fun run(
        instanceHooks: JiraInstanceHooks,
        nodeHooks: List<JiraNodeHooks>
    )
}


interface InstanceUriHook {

    fun run(
        instance: URI,
        instanceHooks: JiraInstanceHooks,
        nodeHooks: List<JiraNodeHooks>
    )
}
