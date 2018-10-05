package com.atlassian.performance.tools.awsinfrastructure.api.storage

import com.atlassian.performance.tools.ssh.api.SshConnection

/**
 * Durable block storage, based on Amazon EBS.
 */
class ElasticBlockStorage : BlockStorage {

    /**
     * No need to mount, because it's mounted already by CloudFormation.
     */
    override fun mount(
        ssh: SshConnection
    ) {
    }
}