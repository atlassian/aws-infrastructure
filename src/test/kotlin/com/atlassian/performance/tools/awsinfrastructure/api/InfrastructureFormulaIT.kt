package com.atlassian.performance.tools.awsinfrastructure.api

import com.atlassian.performance.tools.aws.api.Investment
import com.atlassian.performance.tools.aws.api.currentUser
import com.atlassian.performance.tools.awsinfrastructure.IntegrationTestRuntime
import com.atlassian.performance.tools.awsinfrastructure.api.jira.DataCenterFormula
import com.atlassian.performance.tools.awsinfrastructure.api.jira.JiraFormula
import com.atlassian.performance.tools.awsinfrastructure.api.kibana.Kibana
import com.atlassian.performance.tools.awsinfrastructure.api.kibana.MetricbeatProfiler
import com.atlassian.performance.tools.awsinfrastructure.api.kibana.UbuntuMetricbeat
import com.atlassian.performance.tools.awsinfrastructure.api.network.Network
import com.atlassian.performance.tools.awsinfrastructure.api.network.NetworkFormula
import com.atlassian.performance.tools.awsinfrastructure.api.virtualusers.MetricbeatVirtualUsersFormula
import com.atlassian.performance.tools.awsinfrastructure.api.virtualusers.MultiVirtualUsersFormula
import com.atlassian.performance.tools.awsinfrastructure.api.virtualusers.StackVirtualUsersFormula
import com.atlassian.performance.tools.awsinfrastructure.api.virtualusers.VirtualUsersFormula
import com.atlassian.performance.tools.awsinfrastructure.jira.NetworkOverrideAvoidingJiraFormula
import com.atlassian.performance.tools.awsinfrastructure.virtualusers.NetworkOverrideAvoidingVirtualUsersFormula
import com.atlassian.performance.tools.infrastructure.api.distribution.PublicJiraSoftwareDistribution
import com.atlassian.performance.tools.infrastructure.api.jira.JiraNodeConfig
import com.atlassian.performance.tools.io.api.dereference
import com.atlassian.performance.tools.jiraactions.api.scenario.JiraCoreScenario
import com.atlassian.performance.tools.virtualusers.api.VirtualUserOptions
import com.atlassian.performance.tools.virtualusers.api.config.VirtualUserBehavior
import com.atlassian.performance.tools.virtualusers.api.config.VirtualUserTarget
import org.junit.Test
import java.net.URI
import java.time.Duration
import java.util.*

class InfrastructureFormulaIT {

    @Test
    fun shouldApplyLoad() {
        val aws = IntegrationTestRuntime.aws
        val nonce = UUID.randomUUID().toString()
        val investment = Investment(
            useCase = "test InfrastructureFormula integration",
            lifespan = Duration.ofMinutes(30)
        )
        val kibana = Kibana(
            address = URI("http://34.253.121.248:5601"),
            elasticsearchHosts = listOf(URI("http://34.253.121.248:9200"))
        )
        val network = NetworkFormula(investment, aws).provision()
        val provisionedInfrastructure = InfrastructureFormula(
            investment = investment,
            jiraFormula = prepareDc(network, nonce, kibana),
            virtualUsersFormula = prepareVus(network, nonce, kibana),
            aws = aws
        ).provision(
            IntegrationTestRuntime.taskWorkspace.isolateTest(javaClass.simpleName).directory
        )

        provisionedInfrastructure.infrastructure.applyLoad(object : TargetingVirtualUserOptions {
            override fun target(jira: URI): VirtualUserOptions = VirtualUserOptions(
                target = VirtualUserTarget(
                    webApplication = jira,
                    userName = "admin",
                    password = "admin"
                ),
                behavior = VirtualUserBehavior.Builder(JiraCoreScenario::class.java).build()
            )
        })
    }

    private fun prepareDc(
        network: Network,
        nonce: String,
        kibana: Kibana
    ): JiraFormula {
        val jiraVersion = "7.2.0"
        val dataset = DatasetCatalogue().smallJiraSeven()
        return NetworkOverrideAvoidingJiraFormula(
            DataCenterFormula.Builder(
                productDistribution = PublicJiraSoftwareDistribution(jiraVersion),
                jiraHomeSource = dataset.jiraHomeSource,
                database = dataset.database
            )
                .configs(
                    (1..2).map { nodeNumber ->
                        JiraNodeConfig.Builder()
                            .name("dc-node-$nodeNumber")
                            .profiler(
                                MetricbeatProfiler(
                                    UbuntuMetricbeat(
                                        kibana = kibana,
                                        fields = mapOf(
                                            "jpt-infra-name" to "jira-node-$nodeNumber",
                                            "jpt-infra-role" to "jira-node",
                                            "jpt-jira-version" to jiraVersion,
                                            "jpt-dataset" to dataset.label,
                                            "jpt-nonce" to nonce,
                                            "jpt-user" to currentUser()
                                        )
                                    )
                                )
                            )
                            .build()
                    }
                )
                .network(network)
                .build()
        )
    }

    private fun prepareVus(
        network: Network,
        nonce: String,
        kibana: Kibana
    ): VirtualUsersFormula<*> = NetworkOverrideAvoidingVirtualUsersFormula(
        MultiVirtualUsersFormula(
            base = MetricbeatVirtualUsersFormula(
                base = StackVirtualUsersFormula.Builder(dereference("jpt.virtual-users.shadow-jar"))
                    .network(network)
                    .build(),
                metricbeat = UbuntuMetricbeat(
                    kibana = kibana,
                    fields = mapOf(
                        "jpt-infra-role" to "vu-node",
                        "jpt-nonce" to nonce,
                        "jpt-user" to currentUser()
                    )
                )
            ),
            nodeCount = 2
        )
    )
}