package com.atlassian.performance.tools.awsinfrastructure.api.hardware

import com.amazonaws.services.ec2.model.InstanceType
import com.atlassian.performance.tools.ssh.api.SshConnection

/**
 * C5, 9 XL, ephemeral storage.
 *
 * See [AWS docs](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/InstanceStorage.html).
 * > The data in an instance store persists only during the lifetime of its associated instance.
 * > (...) Do not rely on instance store for valuable, long-term data.
 * If you need your data to survive long periods of time, use a durable computer.
 *
 * @see C4EightExtraLargeElastic
 */
class C5NineExtraLargeEphemeral : Computer() {

    override val instanceType: InstanceType = InstanceType.C5d9xlarge

    override fun setUp(ssh: SshConnection) {
        mountEphemeralStorage(ssh)
    }

    private fun mountEphemeralStorage(ssh: SshConnection) {
        ssh.execute("sudo tar -cf /home/ubuntu.tar .")
        ssh.execute("sudo mkfs.ext4 /dev/nvme1n1")
        ssh.execute("sudo mount -t ext4 /dev/nvme1n1 /home/ubuntu")
        ssh.execute("sudo chown ubuntu /home/ubuntu")
        ssh.execute("cd /home/ubuntu")
        ssh.execute("tar -xf /home/ubuntu.tar")
        ssh.execute("sudo rm /home/ubuntu.tar")
    }
}