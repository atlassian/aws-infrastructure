package com.atlassian.performance.tools.awsinfrastructure.virtualusers

import com.atlassian.performance.tools.aws.api.Storage
import com.atlassian.performance.tools.awsinfrastructure.api.aws.AwsCli
import com.atlassian.performance.tools.infrastructure.api.browser.Browser
import com.atlassian.performance.tools.infrastructure.api.jvm.OpenJDK
import com.atlassian.performance.tools.ssh.api.Ssh
import java.io.File

internal class UbuntuVirtualUsersRuntime {

    /**
     * @return remote JAR path
     */
    fun prepareForExecution(
        sshHost: Ssh,
        shadowJar: File,
        shadowJarTransport: Storage,
        browser: Browser
    ): String {
        shadowJarTransport.upload(shadowJar)
        sshHost.newConnection().use { ssh ->
            ssh.safeExecute("hostname=$(hostname) ; ping -c 1 \$hostname 2>/dev/null || echo $(echo \$hostname | sed -e s/ip-// -e s/-/./g) \$hostname | sudo tee -a /etc/hosts >/dev/null 2>&1")
            AwsCli().download(shadowJarTransport.location, ssh, target = ".")
            browser.install(ssh)
            OpenJDK().install(ssh)
        }
        return shadowJar.name
    }
}
