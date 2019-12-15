package com.atlassian.performance.tools.awsinfrastructure

import com.amazonaws.services.ec2.model.Instance
import com.amazonaws.services.ec2.model.Tag

internal class InstanceFilters {
    private val jiraTag = Tag("jpt-jira", "true")
    private val dbTag = Tag("jpt-database", "true")
    private val sharedHomeTag = Tag("jpt-shared-home", "true")
    private val virtualUsersTag = Tag("jpt-virtual-users", "true")

    internal fun jiraInstances(instances: List<Instance>) =
        instances.filter { it.tags.contains(jiraTag) }

    internal fun dbInstance(instances: List<Instance>) =
        instances.single { it.tags.contains(dbTag) }
    
    internal fun sharedHome(instances: List<Instance>) =
        instances.single { it.tags.contains(sharedHomeTag) }
    
    internal fun vuNodes(instances: List<Instance>) =
        instances.single { it.tags.contains(virtualUsersTag) }
}
