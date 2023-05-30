package com.atlassian.performance.tools.awsinfrastructure.jira.home

import com.atlassian.performance.tools.aws.api.Storage
import com.atlassian.performance.tools.awsinfrastructure.api.aws.AwsCli
import com.atlassian.performance.tools.awsinfrastructure.api.hardware.Computer
import com.atlassian.performance.tools.infrastructure.api.jira.JiraHomeSource
import com.atlassian.performance.tools.infrastructure.api.jira.SharedHome
import com.atlassian.performance.tools.infrastructure.api.os.Ubuntu
import com.atlassian.performance.tools.ssh.api.Ssh
import java.time.Duration

internal class SharedHomeFormula(
    private val pluginsTransport: Storage,
    private val jiraHomeSource: JiraHomeSource,
    private val ip: String,
    private val ssh: Ssh,
    private val computer: Computer,
    private val storeAvatarsInS3: Boolean = false,
    private val storeAttachmentsInS3: Boolean = false,
    private val s3StorageBucketName: String? = null
) {
    private val localSubnet = "10.0.0.0/24"
    private val localSharedHome = "/home/ubuntu/jira-shared-home"

    private val ubuntu = Ubuntu()

    fun provision(): SharedHome {
        val awsCliVersion = "2.9.12"

        ssh.newConnection().use {
            computer.setUp(it)
            val jiraHome = jiraHomeSource.download(it)
            AwsCli(awsCliVersion).download(
                location = pluginsTransport.location,
                ssh = it,
                target = "$jiraHome/plugins/installed-plugins",
                timeout = Duration.ofMinutes(3)
            )

            it.execute("sudo mkdir -p $localSharedHome")
            it.safeExecute("sudo mv $jiraHome/{data,plugins,import,export} $localSharedHome")
            it.safeExecute("sudo mv $jiraHome/logos $localSharedHome")
            ubuntu.install(it, listOf("nfs-kernel-server"))
            it.execute("sudo echo '$localSharedHome $localSubnet(rw,sync,no_subtree_check,no_root_squash)' | sudo tee -a /etc/exports")

            if (s3StorageBucketName != null && (storeAvatarsInS3 || storeAttachmentsInS3)) {
                AwsCli(awsCliVersion).ensureAwsCli(it)
                if (storeAttachmentsInS3) {
                    // Copy the attachment data from NFS onto S3
                    // Use xargs to split the work across 30 concurrent jobs.  This is much faster than a single job.
                    it.safeExecute(
                        cmd = "export AWS_RETRY_MODE=standard; export AWS_MAX_ATTEMPTS=10; cd $localSharedHome/data && ( find attachments -mindepth 1 -maxdepth 1 -type d -print0 | xargs -n1 -0 -P30 -I {} aws s3 cp --recursive --only-show-errors {}/ s3://$s3StorageBucketName/{}/ )",
                        timeout = Duration.ofMinutes(10)
                    )
                }
                if (storeAvatarsInS3) {
                    // Copy the avatar data from NFS onto S3
                    // Split up into subdirs for faster s3 copy then use xargs to split the work across 30 concurrent jobs.
                    it.safeExecute(
                        cmd = "cd $localSharedHome/data/avatars && ( find . -mindepth 1 -maxdepth 1 -type f -print0 | xargs -0 -n 1000 bash -c 'dir=\"subdir_\$(date +%s%N)\"; mkdir -p \"\$dir\"; mv \"\${@:1}\" \"\$dir\"' _ )",
                        timeout = Duration.ofMinutes(3)
                    )
                    it.safeExecute(
                        cmd = "export AWS_RETRY_MODE=standard; export AWS_MAX_ATTEMPTS=10; cd $localSharedHome/data && ( find avatars -mindepth 1 -maxdepth 1 -type d -print0 | xargs -n1 -0 -P30 -I {} aws s3 cp --recursive --only-show-errors {}/ s3://$s3StorageBucketName/avatars )",
                        timeout = Duration.ofMinutes(10)
                    )
                }
            }

            it.execute("sudo service nfs-kernel-server restart")
        }

        return SharedHome(ip, localSharedHome)
    }
}

