package com.atlassian.performance.tools.awsinfrastructure.api.database

import com.amazonaws.services.cloudformation.model.Parameter
import com.atlassian.performance.tools.aws.api.Aws
import com.atlassian.performance.tools.aws.api.Investment
import com.atlassian.performance.tools.aws.api.SshKey
import com.atlassian.performance.tools.aws.api.StackFormula
import com.atlassian.performance.tools.awsinfrastructure.api.Network
import com.atlassian.performance.tools.awsinfrastructure.api.hardware.Computer
import com.atlassian.performance.tools.awsinfrastructure.api.hardware.M5ExtraLargeEphemeral
import com.atlassian.performance.tools.awsinfrastructure.api.hardware.Volume
import com.atlassian.performance.tools.awsinfrastructure.api.jira.JiraNodeProvisioning
import com.atlassian.performance.tools.infrastructure.api.database.DatabaseIpConfig
import com.atlassian.performance.tools.infrastructure.api.database.DockerMysqlServer
import com.atlassian.performance.tools.infrastructure.api.database.MysqlConnector
import com.atlassian.performance.tools.infrastructure.api.dataset.HttpDatasetPackage
import com.atlassian.performance.tools.ssh.api.SshHost
import java.net.URI
import java.time.Duration

class AwsMysqlServer(
    private val mysql: DockerMysqlServer,
    private val aws: Aws,
    private val investment: Investment,
    private val computer: Computer,
    private val volume: Volume,
    private val network: Network,
    private val key: SshKey,
    private val stackCreationTimeout: Duration
) {
    fun provision(): ProvisionedAwsMysqlServer {
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
        mysql.setup(host)
        return ProvisionedAwsMysqlServer(ip)
    }

    companion object { // TODO remove?
        fun default( // TODO convert to builder
            aws: Aws,
            investment: Investment,
            network: Network,
            sshKey: SshKey
        ): AwsMysqlServer = AwsMysqlServer(
            DockerMysqlServer.Builder(
                HttpDatasetPackage(
                    uri = URI("https://s3-eu-west-1.amazonaws.com/")
                        .resolve("jpt-custom-datasets-storage-a008820-datasetbucket-1sjxdtrv5hdhj/")
                        .resolve("dataset-f8dba866-9d1b-492e-b76c-f4a78ac3958c/")
                        .resolve("database.tar.bz2"),
                    downloadTimeout = Duration.ofMinutes(6)
                )
            ).build(),
            aws,
            investment,
            M5ExtraLargeEphemeral(),
            Volume(100),
            network,
            sshKey,
            Duration.ofMinutes(4)
        )
    }
}

class ProvisionedAwsMysqlServer(
    private val ip: String
) {
    fun injectToJiraNodes(nodes: List<JiraNodeProvisioning>) {
        nodes.forEach {
            it.hooks.hook(DatabaseIpConfig(ip))
            it.hooks.hook(MysqlConnector())
        }
    }
}
