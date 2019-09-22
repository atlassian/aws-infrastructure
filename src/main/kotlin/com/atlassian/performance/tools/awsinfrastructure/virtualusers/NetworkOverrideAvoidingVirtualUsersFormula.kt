package com.atlassian.performance.tools.awsinfrastructure.virtualusers

import com.atlassian.performance.tools.awsinfrastructure.api.virtualusers.VirtualUsersFormula
import com.atlassian.performance.tools.infrastructure.api.virtualusers.VirtualUsers

class NetworkOverrideAvoidingVirtualUsersFormula<T : VirtualUsers>(
    private val base: VirtualUsersFormula<T>
) : VirtualUsersFormula<T> by base