package com.atlassian.performance.tools.awsinfrastructure.api.loadbalancer

import com.amazonaws.services.ec2.model.InstanceType.C5Large
import com.atlassian.performance.tools.aws.api.Investment
import com.atlassian.performance.tools.aws.api.SshKeyFormula
import com.atlassian.performance.tools.awsinfrastructure.IntegrationTestRuntime.aws
import com.atlassian.performance.tools.workspace.api.RootWorkspace
import org.junit.Test
import java.time.Duration
import java.util.concurrent.TimeUnit.MINUTES

class ApacheProxyLoadBalancerIT {
    @Test
    fun shouldProvision() {
        val investment = Investment(
            useCase = "ApacheProxyLoadBalancerIT",
            lifespan = Duration.ofMinutes(10)
        )
        val workspace = RootWorkspace().currentTask
        val sshKey = SshKeyFormula(aws.ec2, workspace.directory, investment.reuseKey(), investment.lifespan).provision()
        val (ssh, resource, _) = aws.awaitingEc2.allocateInstance(investment, sshKey, vpcId = null) {
            it.withInstanceType(C5Large)
        }
        val loadBalancer = ApacheProxyLoadBalancer.Builder(ssh).build()

        loadBalancer.provision()

        resource.release().get(5, MINUTES)
    }
}