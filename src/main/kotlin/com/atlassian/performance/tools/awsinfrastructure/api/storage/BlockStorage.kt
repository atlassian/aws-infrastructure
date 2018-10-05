package com.atlassian.performance.tools.awsinfrastructure.api.storage

import com.atlassian.performance.tools.ssh.api.SshConnection

/**
 * Stores blocks of digital data on a remote machine.
 * For example: a HDD, an SSD, Amazon EBS.
 */
interface BlockStorage {

    /**
     * Mount the storage in the instance OS.
     *
     * @param [ssh] connects to the instance
     */
    fun mount(
        ssh: SshConnection
    )
}