package com.atlassian.performance.tools.awsinfrastructure.api.hardware

import com.amazonaws.services.ec2.model.InstanceType
import com.atlassian.performance.tools.ssh.api.SshConnection

/**
 * M4, XL, EBS storage.
 *
 * @since 2.7.0
 */
class M4ExtraLargeElastic : Computer() {

    override val instanceType: InstanceType = InstanceType.M4Xlarge

    /**
     * Assumes Amazon EBS by convention, which requires no additional setup.
     */
    override fun setUp(ssh: SshConnection) {
    }
}
