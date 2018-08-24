package com.atlassian.performance.tools.awsinfrastructure.virtualusers

import com.atlassian.performance.tools.aws.Storage
import com.atlassian.performance.tools.awsinfrastructure.AwsCli
import com.atlassian.performance.tools.infrastructure.api.jvm.OpenJDK
import com.atlassian.performance.tools.infrastructure.api.os.Ubuntu
import com.atlassian.performance.tools.ssh.Ssh
import java.io.File

class UbuntuVirtualUsersRuntime {

    /**
     * @return remote JAR path
     */
    fun prepareForExecution(
        sshHost: Ssh,
        shadowJar: File,
        shadowJarTransport: Storage
    ): String {
        shadowJarTransport.upload(shadowJar)
        val ubuntu = Ubuntu()
        sshHost.newConnection().use { ssh ->
            AwsCli().download(shadowJarTransport.location, ssh, target = ".")
            ssh.execute("wget -q -O - https://dl-ssl.google.com/linux/linux_signing_key.pub | sudo apt-key add")
            ssh.execute("echo 'deb [arch=amd64] http://dl.google.com/linux/chrome/deb/ stable main' | sudo tee -a /etc/apt/sources.list.d/google-chrome.list")
            ubuntu.install(ssh, listOf("google-chrome-stable"))
            OpenJDK().install(ssh)
        }
        return shadowJar.name
    }
}