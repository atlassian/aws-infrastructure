package com.atlassian.performance.tools.awsinfrastructure.aws

import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.model.DescribeInstancesRequest
import com.amazonaws.services.ec2.model.Filter
import com.amazonaws.services.ec2.model.Instance
import com.amazonaws.services.ec2.model.InstanceStateName.*
import com.atlassian.performance.tools.aws.api.ScrollingEc2

/**
 * Scrolls through batches of AWS EC2 instances using "page token".
 */
internal class TokenScrollingEc2(
    private val ec2: AmazonEC2
) : ScrollingEc2 {
    override fun scrollThroughInstances(
        vararg filters: Filter,
        batchAction: (List<Instance>) -> Unit
    ) {
        var token: String? = null
        do {
            val response = ec2.describeInstances(
                DescribeInstancesRequest()
                    .withFilters(filters.toList())
                    .withNextToken(token)
            )
            val batch = response
                .reservations
                .flatMap { it.instances }
            batchAction(batch)
            token = response.nextToken
        } while (token != null)
    }

    override fun findInstances(
        vararg filters: Filter
    ): List<Instance> {
        val instances = mutableListOf<Instance>()
        scrollThroughInstances(*filters) { batch ->
            instances += batch
        }
        return instances
    }

    override fun allocated(): Filter = Filter(
        "instance-state-name",
        listOf(Pending, Running, ShuttingDown).map { it.toString() }
    )
}