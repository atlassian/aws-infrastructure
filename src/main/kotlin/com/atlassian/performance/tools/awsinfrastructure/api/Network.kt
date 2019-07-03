package com.atlassian.performance.tools.awsinfrastructure.api

import com.amazonaws.services.ec2.model.Subnet
import com.amazonaws.services.ec2.model.Vpc

class Network(
    val vpc: Vpc,
    val subnet: Subnet
)
