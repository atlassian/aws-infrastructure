package com.atlassian.performance.tools.awsinfrastructure.api.network.access

import java.util.function.Supplier

class ForIpAccessRequester(
    ipProvider: Supplier<String>
) : AccessRequester by ForCidrAccessRequester(cidrProvider = Supplier { "${ipProvider.get()}/32" })
