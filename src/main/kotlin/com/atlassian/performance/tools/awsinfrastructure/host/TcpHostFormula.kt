package com.atlassian.performance.tools.awsinfrastructure.host

import com.amazonaws.services.cloudformation.model.Parameter
import com.amazonaws.services.ec2.model.InstanceType
import com.atlassian.performance.tools.aws.api.Aws
import com.atlassian.performance.tools.aws.api.Investment
import com.atlassian.performance.tools.aws.api.SshKey
import com.atlassian.performance.tools.aws.api.StackFormula
import com.atlassian.performance.tools.awsinfrastructure.api.Network
import com.atlassian.performance.tools.awsinfrastructure.api.hardware.Computer
import com.atlassian.performance.tools.awsinfrastructure.api.hardware.EbsEc2Instance
import com.atlassian.performance.tools.awsinfrastructure.api.hardware.Volume
import com.atlassian.performance.tools.infrastructure.api.host.TcpHost
import com.atlassian.performance.tools.infrastructure.api.jira.hook.TcpServer
import com.atlassian.performance.tools.io.api.readResourceText
import com.atlassian.performance.tools.ssh.api.Ssh
import com.atlassian.performance.tools.ssh.api.SshHost
import java.time.Duration
import java.util.function.Supplier

class TcpHostFormula private constructor(
    private val port: Int,
    private val name: String,
    private val network: Network,
    private val computer: Computer,
    private val volume: Volume,
    private val aws: Aws,
    private val sshKey: SshKey,
    private val investment: Investment,
    private val stackTimeout: Duration
) : Supplier<TcpHost> {

    override fun get(): TcpHost {
        val stack = StackFormula(
            investment = investment,
            cloudformationTemplate = readResourceText("aws/ssh-tcp-host.yaml"),
            parameters = listOf(
                Parameter()
                    .withParameterKey("TcpPort")
                    .withParameterValue(port.toString()),
                Parameter()
                    .withParameterKey("LogicalName")
                    .withParameterValue(name),
                Parameter()
                    .withParameterKey("KeyName")
                    .withParameterValue(sshKey.remote.name),
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
            pollingTimeout = stackTimeout
        ).provision()
        val ip = stack.listMachines().single().publicIpAddress
        return TcpHost(
            server = TcpServer(
                ip = ip,
                port = port,
                name = name
            ),
            ssh = Ssh(
                host = SshHost(ip, "ubuntu", sshKey.file.path),
                connectivityPatience = 4
            )
        )
    }

    class Builder(
        private var aws: Aws,
        private var sshKey: SshKey,
        private var network: Network
    ) {

        constructor(
            formula: TcpHostFormula
        ) : this(
            formula.aws,
            formula.sshKey,
            formula.network
        ) {
            port = formula.port
            name = formula.name
            computer = formula.computer
            volume = formula.volume
            stackTimeout = formula.stackTimeout
        }

        private var port: Int = 80
        private var name: String = "host"
        private var computer: Computer = EbsEc2Instance(InstanceType.T2Nano)
        private var volume: Volume = Volume(10)
        private var investment: Investment = Investment("Ad hoc provisioning", Duration.ofMinutes(30))
        private var stackTimeout: Duration = Duration.ofMinutes(2)

        fun port(port: Int) = apply { this.port = port }
        fun name(name: String) = apply { this.name = name }
        fun network(network: Network) = apply { this.network = network }
        fun computer(computer: Computer) = apply { this.computer = computer }
        fun volume(volume: Volume) = apply { this.volume = volume }
        fun aws(aws: Aws) = apply { this.aws = aws }
        fun sshKey(sshKey: SshKey) = apply { this.sshKey = sshKey }
        fun investment(investment: Investment) = apply { this.investment = investment }
        fun stackTimeout(stackTimeout: Duration) = apply { this.stackTimeout = stackTimeout }

        fun build() = TcpHostFormula(
            port,
            name,
            network,
            computer,
            volume,
            aws,
            sshKey,
            investment,
            stackTimeout
        )
    }
}
