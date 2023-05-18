package com.atlassian.performance.tools.awsinfrastructure.api.loadbalancer

import com.atlassian.performance.tools.aws.api.StorageLocation
import com.atlassian.performance.tools.infrastructure.api.loadbalancer.LoadBalancer

interface DiagnosableLoadBalancer : LoadBalancer {
    fun gatherEvidence(location: StorageLocation)
}