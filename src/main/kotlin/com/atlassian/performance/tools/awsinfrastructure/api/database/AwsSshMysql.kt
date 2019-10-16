package com.atlassian.performance.tools.awsinfrastructure.api.database

import com.amazonaws.services.cloudformation.model.Parameter
import com.atlassian.performance.tools.aws.api.Aws
import com.atlassian.performance.tools.aws.api.Investment
import com.atlassian.performance.tools.aws.api.SshKey
import com.atlassian.performance.tools.aws.api.StackFormula
import com.atlassian.performance.tools.awsinfrastructure.api.Network
import com.atlassian.performance.tools.awsinfrastructure.api.hardware.Computer
import com.atlassian.performance.tools.awsinfrastructure.api.hardware.Volume
import com.atlassian.performance.tools.infrastructure.api.database.Database
import com.atlassian.performance.tools.infrastructure.api.database.DatabaseIpConfig
import com.atlassian.performance.tools.infrastructure.api.database.MysqlConnector
import com.atlassian.performance.tools.infrastructure.api.jira.hook.PreInstallHooks
import com.atlassian.performance.tools.infrastructure.api.jira.hook.TcpServer
import com.atlassian.performance.tools.infrastructure.api.jira.hook.server.PreInstallHook
import com.atlassian.performance.tools.ssh.api.Ssh
import com.atlassian.performance.tools.ssh.api.SshConnection
import com.atlassian.performance.tools.ssh.api.SshHost
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.net.URI
import java.time.Duration

class AwsSshMysql(
    private val database: Database,
    private val aws: Aws,
    private val investment: Investment,
    private val computer: Computer,
    private val volume: Volume,
    private val network: Network,
    private val key: SshKey,
    private val stackCreationTimeout: Duration
) : PreInstallHook {

    private val logger: Logger = LogManager.getLogger(this::class.java)

    override fun run(ssh: SshConnection, server: TcpServer, hooks: PreInstallHooks) {
        val stack = StackFormula(
            investment = investment,
            cloudformationTemplate = javaClass
                .getResourceAsStream("mysql-ssh.yaml")
                .bufferedReader()
                .use { it.readText() },
            parameters = listOf(
                Parameter()
                    .withParameterKey("KeyName")
                    .withParameterValue(key.remote.name),
                Parameter()
                    .withParameterKey("Ami")
                    .withParameterValue(aws.defaultAmi),
                Parameter()
                    .withParameterKey("InstanceType")
                    .withParameterValue(computer.instanceType.toString()),
                Parameter()
                    .withParameterKey("VolumeSize")
                    .withParameterValue(volume.size.toString()),
                Parameter()
                    .withParameterKey("Vpc")
                    .withParameterValue(network.vpc.vpcId),
                Parameter()
                    .withParameterKey("Subnet")
                    .withParameterValue(network.subnet.subnetId)
            ),
            aws = aws,
            pollingTimeout = stackCreationTimeout
        ).provision()
        val ip = stack.listMachines().single().publicIpAddress
        val host = SshHost(ip, "ubuntu", key.file.path)
        Ssh(host, connectivityPatience = 4).newConnection().use {
            computer.setUp(it)
            logger.info("Setting up database...")
            key.file.facilitateSsh(ip)
            database.setup(it)
            logger.info("Database is set up, starting...")
            database.start(server.toPublicHttp(), it)
            logger.info("Database is started")
        }
        hooks.hook(MysqlConnector())
        hooks.hook(DatabaseIpConfig(ip))
    }
}

private fun TcpServer.toPublicHttp() = URI("http://$ip:$publicPort/")
