package com.atlassian.performance.tools.awsinfrastructure

import com.amazonaws.services.ec2.model.ArchitectureType
import com.amazonaws.services.ec2.model.DescribeImagesRequest
import com.amazonaws.services.ec2.model.DescribeInstanceTypesRequest
import com.amazonaws.services.ec2.model.Filter
import com.amazonaws.services.ec2.model.InstanceType
import com.atlassian.performance.tools.aws.api.Aws
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

internal class AmiNameResolver {
    companion object {
        private val instanceType2Architecture: ConcurrentMap<AwsAndInstanceType, ArchitectureType> = ConcurrentHashMap()
        private val name2amiId: ConcurrentMap<AwsAndAmiName, String> = ConcurrentHashMap()

        fun vuAmi(aws: Aws, instanceType: InstanceType = InstanceType.C59xlarge): String {
            val architecture = instanceType2Architecture.computeIfAbsent(AwsAndInstanceType(aws, instanceType), Companion::resolveArchitecture)

            val vuAmiName = getVuAmiName(architecture)

            return name2amiId.computeIfAbsent(AwsAndAmiName(aws, vuAmiName), Companion::resolveAmiName)
        }

        private fun resolveArchitecture(awsAndInstanceType: AwsAndInstanceType) : ArchitectureType {
            val instanceType = awsAndInstanceType.data

            val describeInstanceTypes = awsAndInstanceType.aws.ec2
                .describeInstanceTypes(DescribeInstanceTypesRequest().withInstanceTypes(instanceType))

            val architectureString = describeInstanceTypes.instanceTypes.single().processorInfo.supportedArchitectures.single()
            return ArchitectureType.fromValue(architectureString)
        }

        private fun resolveAmiName(awsAndAmiName: AwsAndAmiName) : String {
            val ec2 = awsAndAmiName.aws.ec2
            val vuAmiName = awsAndAmiName.data

            return ec2
                .describeImages(
                    DescribeImagesRequest().withFilters(
                        Filter("name", listOf(vuAmiName))
                    )
                )
                .images
                .map { it.imageId }
                .singleOrNull()
                ?: throw Exception("Failed to find image $vuAmiName in ${awsAndAmiName.aws.region}")
        }

        private fun getVuAmiName(architecture: ArchitectureType): String {
            val imgArchitecture = when (architecture) {
                ArchitectureType.Arm64 -> "arm64"
                ArchitectureType.X86_64 -> "amd64"
                else -> {
                    throw IllegalArgumentException("Unsupported architecture: ${architecture}")
                }
            }

            return "ubuntu/images/hvm-ssd/ubuntu-focal-20.04-${imgArchitecture}-server-20200701"
        }
    }

    open class AwsHolder<T>(val aws: Aws, val data : T) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is AwsAndAmiName) return false

            if (aws.region != other.aws.region) return false
            if (data != other.data) return false

            return true
        }

        override fun hashCode(): Int {
            return data!!.hashCode()
        }
    }

    class AwsAndInstanceType(aws: Aws, instanceType: InstanceType) : AwsHolder<InstanceType>(aws, instanceType)

    class AwsAndAmiName(aws: Aws, amiName: String) : AwsHolder<String>(aws, amiName)
}
