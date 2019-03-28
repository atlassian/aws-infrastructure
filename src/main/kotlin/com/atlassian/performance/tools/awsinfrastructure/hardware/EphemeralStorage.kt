package com.atlassian.performance.tools.awsinfrastructure.hardware

import com.atlassian.performance.tools.ssh.api.SshConnection
import java.time.Duration

internal class EphemeralStorage {
    internal fun mount(ssh: SshConnection) {
        ssh.execute("sudo tar -cf /home/ubuntu.tar .")
        ssh.execute("sudo mkfs.ext4 /dev/nvme1n1", Duration.ofMinutes(1))
        ssh.execute("sudo mount -t ext4 /dev/nvme1n1 /home/ubuntu")
        ssh.execute("sudo chown ubuntu /home/ubuntu")
        ssh.execute("cd /home/ubuntu")
        ssh.execute("tar -xf /home/ubuntu.tar")
        ssh.execute("sudo rm /home/ubuntu.tar")
    }
}
