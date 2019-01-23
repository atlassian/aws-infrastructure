package com.atlassian.performance.tools.awsinfrastructure

import com.amazonaws.services.ec2.model.Subnet
import com.amazonaws.services.ec2.model.Vpc

internal class Network(
    val vpc: Vpc,
    val subnet: Subnet
)