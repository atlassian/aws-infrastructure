package com.atlassian.performance.tools.awsinfrastructure.api.hardware

import com.amazonaws.services.ec2.model.InstanceType
import com.atlassian.performance.tools.ssh.api.SshConnection

/**
 * Custom instance type to be passed in. E.g. [InstanceType.M4Xlarge]
 */
class EbsEc2Instance(
    override val instanceType: InstanceType
) : Computer() {

    /**
     * Assumes Amazon EBS by convention, which requires no additional setup.
     */
    override fun setUp(ssh: SshConnection) {
    }
}