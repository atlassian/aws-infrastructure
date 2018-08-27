package com.atlassian.performance.tools.awsinfrastructure.virtualusers

import com.atlassian.performance.tools.aws.*
import com.atlassian.performance.tools.infrastructure.api.virtualusers.LoadProfile
import com.atlassian.performance.tools.infrastructure.api.virtualusers.ResultsTransport
import com.atlassian.performance.tools.infrastructure.api.virtualusers.VirtualUsers
import com.atlassian.performance.tools.jiraactions.scenario.Scenario
import org.apache.logging.log4j.LogManager
import java.net.URI
import java.util.concurrent.Future

class AbsentVirtualUsersFormula : VirtualUsersFormula<AbsentVirtualUsers> {

    private val logger = LogManager.getLogger(this::class.java)

    override fun provision(
        investment: Investment,
        shadowJarTransport: Storage,
        resultsTransport: ResultsTransport,
        key: Future<SshKey>,
        roleProfile: String,
        aws: Aws
    ): ProvisionedVirtualUsers<AbsentVirtualUsers> {
        logger.debug("Virtual users were not provisioned")
        return ProvisionedVirtualUsers(
            virtualUsers = AbsentVirtualUsers(),
            resource = UnallocatedResource()
        )
    }
}

class AbsentVirtualUsers : VirtualUsers {
    private val logger = LogManager.getLogger(this::class.java)

    override fun applyLoad(
        jira: URI,
        loadProfile: LoadProfile,
        scenarioClass: Class<out Scenario>?,
        diagnosticsLimit: Int?
    ) {
        logger.debug("Load will not be applied")
    }

    override fun gatherResults() {
        logger.debug("No results from AbsentVirtualUsers")
    }
}