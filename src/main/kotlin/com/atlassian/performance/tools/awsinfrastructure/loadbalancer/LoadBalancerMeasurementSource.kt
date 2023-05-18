package com.atlassian.performance.tools.awsinfrastructure.loadbalancer

import com.atlassian.performance.tools.aws.api.StorageLocation
import com.atlassian.performance.tools.awsinfrastructure.api.loadbalancer.DiagnosableLoadBalancer
import com.atlassian.performance.tools.infrastructure.api.MeasurementSource

internal class LoadBalancerMeasurementSource(
    private val loadBalancer: DiagnosableLoadBalancer,
    private val target: StorageLocation
) : MeasurementSource {
    internal object Extension {
        fun DiagnosableLoadBalancer.asMeasurementSource(
            target: StorageLocation
        ) = LoadBalancerMeasurementSource(
            loadBalancer = this,
            target = target
        )
    }

    override fun gatherResults() {
        loadBalancer.gatherEvidence(target)
    }
}