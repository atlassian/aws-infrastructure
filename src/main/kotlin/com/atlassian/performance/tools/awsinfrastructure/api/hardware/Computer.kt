package com.atlassian.performance.tools.awsinfrastructure.api.hardware

import com.amazonaws.services.ec2.model.InstanceType
import com.atlassian.performance.tools.ssh.api.SshConnection

abstract class Computer internal constructor() {

    internal abstract val instanceType: InstanceType

    internal abstract fun setUp(ssh: SshConnection)
}