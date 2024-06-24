package com.atlassian.performance.tools.awsinfrastructure

import com.atlassian.performance.tools.aws.api.Investment
import com.atlassian.performance.tools.awsinfrastructure.api.InfrastructureFormula
import com.atlassian.performance.tools.awsinfrastructure.api.dataset.DatasetHost
import com.atlassian.performance.tools.awsinfrastructure.api.hardware.C5NineExtraLargeEphemeral
import com.atlassian.performance.tools.awsinfrastructure.api.jira.StandaloneFormula
import com.atlassian.performance.tools.awsinfrastructure.api.virtualusers.AbsentVirtualUsersFormula
import com.atlassian.performance.tools.infrastructure.api.distribution.PublicJiraSoftwareDistribution
import com.atlassian.performance.tools.infrastructure.api.jira.JiraNodeConfig
import com.atlassian.performance.tools.infrastructure.api.jvm.OpenJDK
import com.atlassian.performance.tools.infrastructure.api.jvm.OracleJDK
import java.time.Duration

/**
 * The default JDK in [JiraNodeConfig] is flaky to install.
 * Currently, it's [OracleJDK] and it's the only one supported by Jira 7. So `infrastructure` cannot just change it,
 * it would break all Jira 7 perf tests. It needs a major release.
 */
object FlakyJdkWorkaround {

    val STABLE_JDK_CONFIG = JiraNodeConfig.Builder()
        .versionedJdk(OpenJDK())
        .build()

    /**
     * avoid unstable default JDK from StandaloneFormula.Builder/JiraNodeConfig.Builder
     * TODO update the default DatasetHost after 3.2.0 release
     */
    val STABLE_DATASET_HOST = DatasetHost {
        InfrastructureFormula
            .Builder(
                aws = IntegrationTestRuntime.aws,
                virtualUsersFormula = AbsentVirtualUsersFormula()
            )
            .investment(
                investment = Investment(
                    useCase = "Generic purpose dataset modification",
                    lifespan = Duration.ofMinutes(50)
                )
            )
            .jiraFormula(
                StandaloneFormula
                    .Builder(PublicJiraSoftwareDistribution("8.0.0"), it.jiraHomeSource, it.database)
                    .config(STABLE_JDK_CONFIG)
                    .computer(C5NineExtraLargeEphemeral())
                    .build()
            )
            .build()
    }
}