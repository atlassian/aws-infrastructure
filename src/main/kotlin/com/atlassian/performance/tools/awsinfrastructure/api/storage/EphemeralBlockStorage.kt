package com.atlassian.performance.tools.awsinfrastructure.api.storage

import com.atlassian.performance.tools.ssh.api.SshConnection

/**
 * Fast, but not durable, block storage.
 *
 * See [AWS docs](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/InstanceStorage.html).
 * > The data in an instance store persists only during the lifetime of its associated instance.
 * > (...) Do not rely on instance store for valuable, long-term data.
 * Instead, use more durable data storage, such as Amazon S3, Amazon EBS, or Amazon EFS.
 *
 * @see ElasticBlockStorage
 */
class EphemeralBlockStorage : BlockStorage {

    override fun mount(
        ssh: SshConnection
    ) {
        ssh.execute("sudo tar -cf /home/ubuntu.tar .")
        ssh.execute("sudo mkfs.ext4 /dev/nvme1n1")
        ssh.execute("sudo mount -t ext4 /dev/nvme1n1 /home/ubuntu")
        ssh.execute("sudo chown ubuntu /home/ubuntu")
        ssh.execute("cd /home/ubuntu")
        ssh.execute("tar -xf /home/ubuntu.tar")
        ssh.execute("sudo rm /home/ubuntu.tar")
    }
}