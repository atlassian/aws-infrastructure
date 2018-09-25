package com.atlassian.performance.tools.awsinfrastructure.api.virtualusers

import com.amazonaws.services.cloudformation.model.Parameter
import com.amazonaws.services.ec2.model.Tag
import com.atlassian.performance.tools.aws.api.*
import com.atlassian.performance.tools.awsinfrastructure.virtualusers.UbuntuVirtualUsersRuntime
import com.atlassian.performance.tools.infrastructure.api.splunk.DisabledSplunkForwarder
import com.atlassian.performance.tools.infrastructure.api.splunk.SplunkForwarder
import com.atlassian.performance.tools.infrastructure.api.virtualusers.ResultsTransport
import com.atlassian.performance.tools.infrastructure.api.virtualusers.SshVirtualUsers
import com.atlassian.performance.tools.io.api.readResourceText
import com.atlassian.performance.tools.ssh.api.Ssh
import com.atlassian.performance.tools.ssh.api.SshHost
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.io.File
import java.util.concurrent.Future

/**
 * @param [splunkForwarder] Forwards logs from `/home/ubuntu/splunkforward`.
 */
class StackVirtualUsersFormula(
    private val nodeOrder: Int = 1,
    private val shadowJar: File,
    private val splunkForwarder: SplunkForwarder
) : VirtualUsersFormula<SshVirtualUsers> {
    private val logger: Logger = LogManager.getLogger(this::class.java)

    private val name: String = "virtual-user-node-$nodeOrder"

    @Deprecated(
        message = "Use the primary constructor"
    )
    constructor(
        nodeOrder: Int = 1,
        shadowJar: File
    ) : this(
        nodeOrder = nodeOrder,
        shadowJar = shadowJar,
        splunkForwarder = DisabledSplunkForwarder()
    )

    override fun provision(
        investment: Investment,
        shadowJarTransport: Storage,
        resultsTransport: ResultsTransport,
        key: Future<SshKey>,
        roleProfile: String,
        aws: Aws
    ): ProvisionedVirtualUsers<SshVirtualUsers> {
        logger.info("Setting up $name...")

        val virtualUsersStack = StackFormula(
            investment = investment,
            cloudformationTemplate = readResourceText("aws/virtual-users.yaml"),
            parameters = listOf(
                Parameter()
                    .withParameterKey("KeyName")
                    .withParameterValue(key.get().remote.name),
                Parameter()
                    .withParameterKey("InstanceProfile")
                    .withParameterValue(roleProfile),
                Parameter()
                    .withParameterKey("Ami")
                    .withParameterValue(aws.defaultAmi)
            ),
            aws = aws
        ).provision()

        val virtualUsersMachine = virtualUsersStack
            .listMachines()
            .single { it.tags.contains(Tag("jpt-virtual-users", "true")) }
        val virtualUsersIp = virtualUsersMachine.publicIpAddress
        val virtualUsersHost = SshHost(virtualUsersIp, "ubuntu", key.get().file.path)
        val virtualUsersSsh = Ssh(virtualUsersHost)

        key.get().file.facilitateSsh(virtualUsersIp)

        val jarPath = UbuntuVirtualUsersRuntime().prepareForExecution(
            virtualUsersSsh,
            shadowJar,
            shadowJarTransport
        )
        virtualUsersSsh.newConnection().use {
            it.execute("mkdir splunkforward")
            splunkForwarder.run(it, name, logsPath = "/home/ubuntu/splunkforward/")
        }
        logger.info("$name is ready to apply load")
        return ProvisionedVirtualUsers(
            virtualUsers = SshVirtualUsers(
                nodeOrder = nodeOrder,
                name = name,
                resultsTransport = resultsTransport,
                jarName = jarPath,
                ssh = virtualUsersSsh
            ),
            resource = virtualUsersStack
        )
    }
}