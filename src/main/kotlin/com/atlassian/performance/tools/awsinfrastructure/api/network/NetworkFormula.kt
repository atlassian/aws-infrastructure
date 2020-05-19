package com.atlassian.performance.tools.awsinfrastructure.api.network

import com.amazonaws.services.cloudformation.model.Parameter
import com.atlassian.performance.tools.aws.api.Aws
import com.atlassian.performance.tools.aws.api.Investment
import com.atlassian.performance.tools.aws.api.StackFormula
import com.atlassian.performance.tools.awsinfrastructure.pickAvailabilityZone
import com.atlassian.performance.tools.io.api.readResourceText
import org.apache.logging.log4j.LogManager

/**
 * @since 2.14.0
 */
class NetworkFormula(
    private val investment: Investment,
    private val aws: Aws
) {
    private val logger = LogManager.getLogger(this::class.java)

    fun provision(): Network {
        val stackFormula = StackFormula(
            investment = investment,
            aws = aws,
            cloudformationTemplate = readResourceText("aws/network.yaml"),
            parameters = listOf(
                Parameter()
                    .withParameterKey("AvailabilityZone")
                    .withParameterValue(aws.pickAvailabilityZone().zoneName)
            )
        )
        logger.info("Provisioning network...")
        val stack = stackFormula.provision()
        val network = Network(
            stack.findVpc("Vpc"),
            stack.findSubnet("TheOnlySubnet")
        )
        logger.info("Network provisioned: $network")
        return network
    }
}
