package com.atlassian.performance.tools.awsinfrastructure.loadbalancer

import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancing
import com.amazonaws.services.elasticloadbalancing.model.DescribeInstanceHealthRequest
import com.amazonaws.services.elasticloadbalancing.model.LoadBalancerDescription
import com.atlassian.performance.tools.infrastructure.api.loadbalancer.LoadBalancer
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.net.URI
import java.time.Duration
import java.time.Instant

class ElasticLoadBalancer(
    private val loadBalancer: AmazonElasticLoadBalancing,
    private val loadBalancerDescription: LoadBalancerDescription
) : LoadBalancer {
    override val uri = URI("http://${loadBalancerDescription.dnsName}/")

    private val logger: Logger = LogManager.getLogger(this::class.java)

    override fun waitUntilHealthy(timeout: Duration) {
        logger.info("Starting load balancer...")
        val start = Instant.now()
        while (!isHealthy()) {
            if (Instant.now() > start + timeout) {
                throw RuntimeException("Load balancer didn't start in time")
            }
            Thread.sleep(Duration.ofSeconds(5).toMillis())
        }
        logger.info("Load balancer is started")
    }

    private fun isHealthy(): Boolean {
        val instanceStates = loadBalancer
            .describeInstanceHealth(
                DescribeInstanceHealthRequest()
                    .withLoadBalancerName(loadBalancerDescription.loadBalancerName)
            )
            .instanceStates
        return !instanceStates.any { it.state != "InService" }
    }
}