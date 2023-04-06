package com.atlassian.performance.tools.awsinfrastructure.jira.home

import com.amazonaws.services.ec2.model.IamInstanceProfileSpecification
import com.atlassian.performance.tools.aws.api.Investment
import com.atlassian.performance.tools.aws.api.SshKeyFormula
import com.atlassian.performance.tools.awsinfrastructure.IntegrationTestRuntime.aws
import com.atlassian.performance.tools.awsinfrastructure.api.DatasetCatalogue
import com.atlassian.performance.tools.awsinfrastructure.api.hardware.C5NineExtraLargeEphemeral
import com.atlassian.performance.tools.workspace.api.RootWorkspace
import org.junit.Test
import java.time.Duration
import java.util.*
import java.util.concurrent.TimeUnit.MINUTES

class SharedHomeFormulaIT {

    @Test
    fun shouldProvisionAfterModification() {
        val nonce = "SharedHomeFormulaIT-${UUID.randomUUID()}"
        val investment = Investment(
            useCase = "SharedHomeFormulaIT",
            lifespan = Duration.ofMinutes(40),
            reuseKey = { nonce }
        )
        val workspace = RootWorkspace().currentTask.isolateTest(nonce)
        val originalDataset = DatasetCatalogue().mediumJiraNine()
        val transport = aws.jiraStorage(nonce)
        val computer = C5NineExtraLargeEphemeral()
        val sshKey = SshKeyFormula(aws.ec2, workspace.directory, investment.reuseKey(), investment.lifespan).provision()
        val (ssh, resource, instance) = aws.awaitingEc2.allocateInstance(investment, sshKey, vpcId = null) {
            it.withIamInstanceProfile(IamInstanceProfileSpecification().withName(aws.shortTermStorageAccess()))
            it.withInstanceType(computer.instanceType)
        }
        val balancerIp = instance.publicIpAddress

        SharedHomeFormula(transport, originalDataset.jiraHomeSource, balancerIp, ssh, computer).provision()

        resource.release().get(4, MINUTES)
    }
}