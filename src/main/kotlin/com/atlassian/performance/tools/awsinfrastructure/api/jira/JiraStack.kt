package com.atlassian.performance.tools.awsinfrastructure.api.jira

import com.amazonaws.services.cloudformation.model.Parameter
import com.amazonaws.services.ec2.model.Tag
import com.atlassian.performance.tools.aws.api.*
import com.atlassian.performance.tools.awsinfrastructure.NetworkFormula
import com.atlassian.performance.tools.awsinfrastructure.TemplateBuilder
import com.atlassian.performance.tools.awsinfrastructure.api.Network
import com.atlassian.performance.tools.awsinfrastructure.api.hardware.C4EightExtraLargeElastic
import com.atlassian.performance.tools.awsinfrastructure.api.hardware.Computer
import com.atlassian.performance.tools.awsinfrastructure.api.hardware.M4ExtraLargeElastic
import com.atlassian.performance.tools.awsinfrastructure.api.hardware.Volume
import com.atlassian.performance.tools.awsinfrastructure.api.loadbalancer.ApacheEc2LoadBalancerFormula
import com.atlassian.performance.tools.awsinfrastructure.api.loadbalancer.LoadBalancerFormula
import com.atlassian.performance.tools.concurrency.api.submitWithLogContext
import com.atlassian.performance.tools.infrastructure.api.Infrastructure
import com.atlassian.performance.tools.infrastructure.api.app.Apps
import com.atlassian.performance.tools.infrastructure.api.jira.install.HttpNode
import com.atlassian.performance.tools.infrastructure.api.jira.install.TcpNode
import com.atlassian.performance.tools.ssh.api.Ssh
import com.atlassian.performance.tools.ssh.api.SshHost
import com.google.common.util.concurrent.ThreadFactoryBuilder
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.Future

class JiraStack private constructor(
    private val aws: Aws,
    private val network: Network,
    private val investment: Investment,
    private val sshKey: Future<SshKey>,
    private val roleProfile: String,
    private val jiraComputer: Computer,
    private val jiraVolume: Volume,
    private val databaseComputer: Computer,
    private val databaseVolume: Volume,
    private val provisioningTimout: Duration
) : Infrastructure {
    private val logger: Logger = LogManager.getLogger(this::class.java)
    private val stack: Lazy<ProvisionedStack> = lazy { provision() }

    private fun provision(): ProvisionedStack {
        logger.info("Setting up Jira stack...")
        val template = TemplateBuilder("2-nodes-dc.yaml").adaptTo(configs)
        val stack = StackFormula(
            investment = investment,
            cloudformationTemplate = template,
            parameters = listOf(
                Parameter()
                    .withParameterKey("KeyName")
                    .withParameterValue(sshKey.get().remote.name),
                Parameter()
                    .withParameterKey("InstanceProfile")
                    .withParameterValue(roleProfile),
                Parameter()
                    .withParameterKey("Ami")
                    .withParameterValue(aws.defaultAmi),
                Parameter()
                    .withParameterKey("JiraInstanceType")
                    .withParameterValue(jiraComputer.instanceType.toString()),
                Parameter()
                    .withParameterKey("JiraVolumeSize")
                    .withParameterValue(jiraVolume.size.toString()),
                Parameter()
                    .withParameterKey("DatabaseInstanceType")
                    .withParameterValue(databaseComputer.instanceType.toString()),
                Parameter()
                    .withParameterKey("DatabaseVolumeSize")
                    .withParameterValue(databaseVolume.size.toString()),
                Parameter()
                    .withParameterKey("Vpc")
                    .withParameterValue(network.vpc.vpcId),
                Parameter()
                    .withParameterKey("Subnet")
                    .withParameterValue(network.subnet.subnetId)
            ),
            aws = aws,
            pollingTimeout = provisioningTimout
        ).provision()
        logger.info("Jira stack is provisioned, it will expire at ${stack.expiry}")
        return stack
    }

    override val subnet: String
        get() = network.subnet.cidrBlock

    override fun close() {
        stack.value.release().get()
    }

    override fun serve(name: String, tcpPorts: List<Int>, udpPorts: List<Int>): TcpNode {
        throw Exception("Stack doesn't know how to serve a generic HTTP node, use specialized `for*` methods instead")
    }

    override fun serveHttp(name: String): HttpNode {
        throw Exception("Stack doesn't know how to serve a default HTTP node, use specialized `for*` methods instead")
    }

    override fun serveSsh(name: String): Ssh {
        throw Exception("Stack doesn't know how to serve a default SSH node, use specialized `for*` methods instead")
    }

    override fun serveTcp(name: String): TcpNode {
        throw Exception("Stack doesn't know how to serve a default TCP node, use specialized `for*` methods instead")
    }

    fun forDatabase(): Infrastructure {
        return StackDatabase(stack, databaseComputer, subnet, sshKey.get())
    }

    fun forSharedHome(): Infrastructure {
        return StackSharedHome(stack, jiraComputer, subnet, sshKey.get())
    }

    fun forJiraNodes(): Infrastructure {
        return StackJiraNodes(stack, jiraComputer, subnet, sshKey.get())
    }

    fun forLoadBalancer(): Infrastructure {
        return StackJiraNodes(stack, jiraComputer, subnet, sshKey.get())
    }

    private class StackSharedHome(
        private val stack: Lazy<ProvisionedStack>,
        private val computer: Computer,
        override val subnet: String,
        private val sshKey: SshKey
    ) : Infrastructure {

        override fun close() {
            stack.value.release().get()
        }

        override fun serve(name: String, tcpPorts: List<Int>, udpPorts: List<Int>): TcpNode {
            throw Exception("The stack doesn't expect a TCP port for shared home")
        }

        override fun serveHttp(name: String): HttpNode {
            throw Exception("The stack doesn't provide a HTTP node for the shared home")
        }

        override fun serveSsh(name: String): Ssh {
            val machine = stack.value.listMachines().single { it.tags.contains(Tag("jpt-shared-home", "true")) }
            val publicIp = machine.publicIpAddress
            val ssh = Ssh(SshHost(publicIp, "ubuntu", sshKey.file.path), connectivityPatience = 4)
            sshKey.file.facilitateSsh(publicIp)
            ssh.newConnection().use { computer.setUp(it) }
            return ssh
        }

        override fun serveTcp(name: String): TcpNode {
            throw Exception("The stack doesn't expect a TCP port for shared home")
        }
    }

    private class StackDatabase(
        private val stack: Lazy<ProvisionedStack>,
        private val computer: Computer,
        override val subnet: String,
        private val sshKey: SshKey
    ) : Infrastructure {

        override fun close() {
            stack.value.release().get()
        }

        override fun serve(name: String, tcpPorts: List<Int>, udpPorts: List<Int>): TcpNode {
            if (tcpPorts.singleOrNull() == 3306 && udpPorts.isEmpty()) {
                return serveTcp(name)
            } else {
                throw Exception("The stack is not prepared for TCP $tcpPorts and UDP $udpPorts")
            }
        }

        override fun serveHttp(name: String): HttpNode {
            throw Exception("The stack doesn't provide a HTTP node for the database")
        }

        override fun serveSsh(name: String): Ssh {
            return serveTcp(name).ssh
        }

        override fun serveTcp(name: String): TcpNode {
            val machine = stack.value.listMachines().single { it.tags.contains(Tag("jpt-database", "true")) }
            val publicIp = machine.publicIpAddress
            val ssh = Ssh(SshHost(publicIp, "ubuntu", sshKey.file.path), connectivityPatience = 5)
            sshKey.file.facilitateSsh(publicIp)
            ssh.newConnection().use { computer.setUp(it) }
            return TcpNode(
                publicIp,
                machine.privateIpAddress,
                3306,
                name,
                ssh
            )
        }
    }

    private class StackJiraNodes(
        private val stack: Lazy<ProvisionedStack>,
        private val computer: Computer,
        override val subnet: String,
        private val sshKey: SshKey
    ) : Infrastructure {

        private val machines by lazy {
            stack.value.listMachines().filter { it.tags.contains(Tag("jpt-jira", "true")) }
        }
        private var nodesRequested = 0

        override fun close() {
            stack.value.release().get()
        }

        override fun serve(name: String, tcpPorts: List<Int>, udpPorts: List<Int>): TcpNode {
            if (tcpPorts.singleOrNull() == 3306 && udpPorts.isEmpty()) {
                return serveTcp(name)
            } else {
                throw Exception("The stack is not prepared for TCP $tcpPorts and UDP $udpPorts")
            }
        }

        override fun serveHttp(name: String): HttpNode {
            val machine = machines[nodesRequested++]
            val publicIp = machine.publicIpAddress
            val ssh = Ssh(SshHost(publicIp, "ubuntu", sshKey.file.path), connectivityPatience = 5)
            sshKey.file.facilitateSsh(publicIp)
            ssh.newConnection().use { computer.setUp(it) }
            val tcp = TcpNode(
                publicIp,
                machine.privateIpAddress,
                3306,
                name,
                ssh
            )
            return HttpNode(tcp, "/", false)
        }

        override fun serveSsh(name: String): Ssh {
            return serveTcp(name).ssh
        }

        override fun serveTcp(name: String): TcpNode {
            return serveHttp(name).tcp
        }
    }

    class Builder(
        private var aws: Aws,
        private var network: Network,
        private var investment: Investment,
        private var sshKey: Future<SshKey>,
        private var roleProfile: String
    ) {
        private var jiraComputer: Computer = C4EightExtraLargeElastic()
        private var jiraVolume: Volume = Volume(100)
        private var databaseComputer: Computer = M4ExtraLargeElastic()
        private var databaseVolume: Volume = Volume(100)
        private var provisioningTimout: Duration = Duration.ofMinutes(30)

        fun jiraComputer(jiraComputer: Computer) = apply { this.jiraComputer = jiraComputer }
        fun jiraVolume(jiraVolume: Volume) = apply { this.jiraVolume = jiraVolume }
        fun databaseComputer(databaseComputer: Computer) = apply { this.databaseComputer = databaseComputer }
        fun databaseVolume(databaseVolume: Volume) = apply { this.databaseVolume = databaseVolume }
        fun provisioningTimout(provisioningTimout: Duration) = apply { this.provisioningTimout = provisioningTimout }

        fun build(): JiraStack = JiraStack(
            aws = aws,
            network = network,
            investment = investment,
            sshKey = sshKey,
            roleProfile = roleProfile,
            jiraComputer = jiraComputer,
            jiraVolume = jiraVolume,
            databaseComputer = databaseComputer,
            databaseVolume = databaseVolume,
            provisioningTimout = provisioningTimout
        )
    }
}

