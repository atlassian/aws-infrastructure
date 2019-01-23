package com.atlassian.performance.tools.awsinfrastructure

import com.amazonaws.services.cloudformation.model.Parameter
import com.atlassian.performance.tools.aws.api.Aws
import com.atlassian.performance.tools.aws.api.Investment
import com.atlassian.performance.tools.aws.api.StackFormula
import com.atlassian.performance.tools.io.api.readResourceText

internal class NetworkFormula(
    private val investment: Investment,
    private val aws: Aws
) {

    fun provision(): Network {
        return StackFormula(
            investment = investment,
            aws = aws,
            cloudformationTemplate = readResourceText("aws/network.yaml"),
            parameters = listOf(
                Parameter()
                    .withParameterKey("AvailabilityZone")
                    .withParameterValue(aws.pickAvailabilityZone().zoneName)
            )
        )
            .provision()
            .let {
                Network(
                    it.findVpc("Vpc"),
                    it.findSubnet("TheOnlySubnet")
                )
            }
    }
}