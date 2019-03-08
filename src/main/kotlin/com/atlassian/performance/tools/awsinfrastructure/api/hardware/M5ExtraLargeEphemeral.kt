package com.atlassian.performance.tools.awsinfrastructure.api.hardware

import com.amazonaws.services.ec2.model.InstanceType
import com.atlassian.performance.tools.awsinfrastructure.hardware.EphemeralStorage
import com.atlassian.performance.tools.ssh.api.SshConnection

/**
 * M5, XL, ephemeral storage.
 *
 * See [AWS docs](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/InstanceStorage.html).
 * > The data in an instance store persists only during the lifetime of its associated instance.
 * > (...) Do not rely on instance store for valuable, long-term data.
 * If you need your data to survive long periods of time, use a durable computer.
 *
 * @since 2.7.0
 * @see M4ExtraLargeElastic
 */
class M5ExtraLargeEphemeral : Computer() {

    override val instanceType: InstanceType = InstanceType.M5dXlarge

    override fun setUp(ssh: SshConnection) {
        EphemeralStorage().mount(ssh)
    }
}
