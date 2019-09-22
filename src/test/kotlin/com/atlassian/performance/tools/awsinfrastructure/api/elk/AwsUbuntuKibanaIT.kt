package com.atlassian.performance.tools.awsinfrastructure.api.elk

import com.atlassian.performance.tools.aws.api.Investment
import com.atlassian.performance.tools.aws.api.SshKeyFormula
import com.atlassian.performance.tools.awsinfrastructure.IntegrationTestRuntime
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.time.Duration
import java.util.*

class AwsUbuntuKibanaIT {

    private val aws = IntegrationTestRuntime.aws
    private val workspace = IntegrationTestRuntime.taskWorkspace.isolateTest(javaClass.simpleName)
    private val logger = IntegrationTestRuntime.logContext.getLogger(this::class.java.canonicalName)

    @Test
    fun shouldProvision() {
        val nonce = UUID.randomUUID().toString()
        val lifespan = Duration.ofDays(4)
        val sshKey = SshKeyFormula(
            ec2 = aws.ec2,
            workingDirectory = workspace.directory,
            lifespan = lifespan,
            prefix = nonce
        ).provision()
        val investment = Investment(
            "Test AwsUbuntuKibana",
            lifespan = lifespan,
            disposable = false
        )

        val kibana = AwsUbuntuKibana().provision(
            aws.ec2,
            aws.awaitingEc2,
            sshKey,
            investment
        )

        logger.info("Kibana provisioned: ${kibana.address}, ES: ${kibana.elasticsearchHosts}")
        assertThat(kibana.address.scheme).isEqualTo("http")
    }
}
