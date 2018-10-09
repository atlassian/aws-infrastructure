package com.atlassian.performance.tools.awsinfrastructure.api.hardware

import com.amazonaws.services.ec2.model.InstanceType
import com.atlassian.performance.tools.ssh.api.SshConnection

/**
 * C4, 8 XL, EBS storage.
 */
class C4EightExtraLargeElastic : Computer() {

    override val instanceType: InstanceType = InstanceType.C48xlarge

    /**
     * Assumes Amazon EBS by convention, which requires no additional setup.
     */
    override fun setUp(ssh: SshConnection) {
    }
}