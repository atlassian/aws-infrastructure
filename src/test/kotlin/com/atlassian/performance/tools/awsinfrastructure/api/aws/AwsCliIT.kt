package com.atlassian.performance.tools.awsinfrastructure.api.aws

import com.amazonaws.services.ec2.model.InstanceType
import com.atlassian.performance.tools.aws.api.Investment
import com.atlassian.performance.tools.aws.api.SshInstance
import com.atlassian.performance.tools.aws.api.SshKeyFormula
import com.atlassian.performance.tools.awsinfrastructure.IntegrationTestRuntime
import com.atlassian.performance.tools.awsinfrastructure.api.network.NetworkFormula
import org.assertj.core.api.Assertions
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.Duration
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class AwsCliIT {

    private val workspace = IntegrationTestRuntime.taskWorkspace.isolateTest(javaClass.simpleName)
    private val aws = IntegrationTestRuntime.aws
    private lateinit var executor: ExecutorService
    private lateinit var sshInstance: SshInstance

    @Before
    fun setUp() {
        executor = Executors.newCachedThreadPool()
        val nonce = UUID.randomUUID().toString()
        val lifespan = Duration.ofMinutes(5)
        val keyFormula = SshKeyFormula(
            ec2 = IntegrationTestRuntime.aws.ec2,
            workingDirectory = workspace.directory,
            lifespan = lifespan,
            prefix = nonce
        )
        val investment = Investment(
            useCase = "AwsCli Integration test",
            lifespan = lifespan
        )
        val networkFormula = NetworkFormula(investment, aws)
        val network = networkFormula.provision()
        sshInstance = aws.awaitingEc2.allocateInstance(
            investment = investment,
            key = keyFormula.provision(),
            vpcId = network.vpc.vpcId,
            customizeLaunch = { launch ->
                launch
                    .withSubnetId(network.subnet.subnetId)
                    .withInstanceType(InstanceType.T3Nano)
            }
        )
    }

    @After
    fun cleanUp() {
        sshInstance.resource.release()
        executor.shutdownNow()
    }

    @Test
    fun shouldBeParallelOnDifferentSshConnections() {
        val concurrency = 4

        val ensureCliErrors = (1..concurrency).map {
            executor.submit {
                sshInstance.ssh.newConnection().use { connection ->
                    AwsCli().ensureAwsCli(connection)
                }
            }
        }.map {
            Assertions.catchThrowable { it.get(2, TimeUnit.MINUTES) }
        }

        Assertions.assertThat(ensureCliErrors).containsOnlyNulls()
    }
}
