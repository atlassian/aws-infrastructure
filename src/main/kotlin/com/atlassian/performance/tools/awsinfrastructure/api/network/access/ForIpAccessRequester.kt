package com.atlassian.performance.tools.awsinfrastructure.api.network.access

class ForIpAccessRequester(
    ipProvider: () -> String
) : AccessRequester by ForCidrAccessRequester(cidrProvider = { "${ipProvider()}/32" })
