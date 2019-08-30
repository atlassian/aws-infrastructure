package com.atlassian.performance.tools.awsinfrastructure.api.elk

import java.net.URI

class Kibana(
    val address: URI,
    val elasticsearchHosts: List<URI>
)
