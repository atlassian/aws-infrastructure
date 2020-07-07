package com.atlassian.performance.tools.awsinfrastructure

import com.amazonaws.services.ec2.model.DescribeImagesRequest
import com.amazonaws.services.ec2.model.Filter
import com.atlassian.performance.tools.aws.api.Aws
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

internal class AmiNameResolver {
    companion object {
        private const val vuAmiName = "ubuntu/images/hvm-ssd/ubuntu-focal-20.04-amd64-server-20200701"

        private val name2amiId: ConcurrentMap<AwsAndAmi, String> = ConcurrentHashMap()

        fun vuAmi(aws: Aws): String {
            return name2amiId.computeIfAbsent(AwsAndAmi(aws, vuAmiName), Companion::resolveAmiName)
        }

        private fun resolveAmiName(awsAndAmi: AwsAndAmi) : String {
            return awsAndAmi.aws.ec2
                .describeImages(
                    DescribeImagesRequest().withFilters(
                        Filter("name", listOf(vuAmiName))
                    )
                )
                .images
                .map { it.imageId }
                .singleOrNull()
                ?: throw Exception("Failed to find image $vuAmiName in ${awsAndAmi.aws.region}")
        }
    }

    data class AwsAndAmi constructor(val aws: Aws, val ami: String) {

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is AwsAndAmi) return false

            if (aws.region != other.aws.region) return false
            if (ami != other.ami) return false

            return true
        }

        override fun hashCode(): Int {
            return ami.hashCode()
        }
    }
}
