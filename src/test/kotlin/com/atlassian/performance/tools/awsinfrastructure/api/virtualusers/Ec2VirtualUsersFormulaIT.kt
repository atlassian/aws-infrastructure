package com.atlassian.performance.tools.awsinfrastructure.api.virtualusers

import com.atlassian.performance.tools.aws.api.Investment
import com.atlassian.performance.tools.aws.api.SshKeyFormula
import com.atlassian.performance.tools.awsinfrastructure.IntegrationTestRuntime.aws
import com.atlassian.performance.tools.awsinfrastructure.NetworkFormula
import com.atlassian.performance.tools.awsinfrastructure.virtualusers.S3ResultsTransport
import org.junit.Test
import java.nio.file.Files
import java.time.Duration
import java.util.*
import java.util.concurrent.CompletableFuture.completedFuture

class Ec2VirtualUsersFormulaIT {

    @Test
    fun shouldProvision() {
        val jar = Files.createTempFile("vu", ".jar").toFile()
        val investment = Investment(
            useCase = "test Ec2VirtualUsersFormula",
            lifespan = Duration.ofMinutes(5)
        )
        val networkFormula = NetworkFormula(investment, aws)
        val nonce = UUID.randomUUID().toString()
        val keyFormula = SshKeyFormula(
            aws.ec2,
            Files.createTempDirectory("ssh"),
            nonce,
            investment.lifespan
        )

        Ec2VirtualUsersFormula.Builder(jar)
            .network(networkFormula.provision())
            .build()
            .provision(
                investment = investment,
                shadowJarTransport = aws.virtualUsersStorage(nonce),
                resultsTransport = S3ResultsTransport(aws.resultsStorage(nonce)),
                key = completedFuture(keyFormula.provision()),
                roleProfile = aws.shortTermStorageAccess(),
                aws = aws
            )
    }
}
