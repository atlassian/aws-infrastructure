package com.atlassian.performance.tools.awsinfrastructure.api

import com.atlassian.performance.tools.jiraactions.api.scenario.Scenario
import com.atlassian.performance.tools.virtualusers.api.VirtualUserLoad
import com.atlassian.performance.tools.virtualusers.api.VirtualUserOptions
import java.net.URI

class VirtualUsersConfiguration(
    private val virtualUserLoad: VirtualUserLoad = DEFAULTS.virtualUserLoad,
    private val scenario: Class<out Scenario> = DEFAULTS.scenario,
    private val seed: Long = DEFAULTS.seed,
    private val diagnosticsLimit: Int = DEFAULTS.diagnosticsLimit
) {
    private companion object {
        private val DEFAULTS = VirtualUserOptions()
    }

    fun configure(
        jiraAddress: URI = DEFAULTS.jiraAddress,
        adminLogin: String = DEFAULTS.adminLogin,
        adminPassword: String = DEFAULTS.adminPassword
    ): VirtualUserOptions {
        return VirtualUserOptions(
            help = DEFAULTS.help,
            jiraAddress = jiraAddress,
            adminLogin = adminLogin,
            adminPassword = adminPassword,
            virtualUserLoad = virtualUserLoad,
            scenario = scenario,
            seed = seed,
            diagnosticsLimit = diagnosticsLimit
        )
    }
}