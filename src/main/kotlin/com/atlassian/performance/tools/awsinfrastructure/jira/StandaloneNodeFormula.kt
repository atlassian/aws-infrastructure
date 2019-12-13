package com.atlassian.performance.tools.awsinfrastructure.jira

import com.atlassian.performance.tools.aws.api.Storage
import com.atlassian.performance.tools.awsinfrastructure.api.aws.AwsCli
import com.atlassian.performance.tools.awsinfrastructure.api.hardware.Computer
import com.atlassian.performance.tools.concurrency.api.submitWithLogContext
import com.atlassian.performance.tools.infrastructure.api.Sed
import com.atlassian.performance.tools.infrastructure.api.distribution.ProductDistribution
import com.atlassian.performance.tools.infrastructure.api.jira.JiraGcLog
import com.atlassian.performance.tools.infrastructure.api.jira.JiraHomeSource
import com.atlassian.performance.tools.infrastructure.api.jira.JiraNodeConfig
import com.atlassian.performance.tools.infrastructure.api.jira.SetenvSh
import com.atlassian.performance.tools.infrastructure.api.os.Ubuntu
import com.atlassian.performance.tools.jvmtasks.api.Backoff
import com.atlassian.performance.tools.jvmtasks.api.IdempotentAction
import com.atlassian.performance.tools.ssh.api.Ssh
import com.atlassian.performance.tools.ssh.api.SshConnection
import com.google.common.util.concurrent.ThreadFactoryBuilder
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.time.Duration
import java.util.concurrent.Executors

internal class StandaloneNodeFormula(
    private val jiraHomeSource: JiraHomeSource,
    private val pluginsTransport: Storage,
    private val resultsTransport: Storage,
    private val databaseIp: String,
    private val productDistribution: ProductDistribution,
    private val ssh: Ssh,
    private val config: JiraNodeConfig,
    private val computer: Computer
) : NodeFormula {
    private val logger: Logger = LogManager.getLogger(this::class.java)
    private val jdk = config.jdk
    private val ubuntu: Ubuntu = Ubuntu()

    override val name = config.name

    override fun provision(): StandaloneStoppedNode {
        logger.debug("Setting up $name...")
        ssh.newConnection().use { connection ->
            computer.setUp(connection)
        }
        val executor = Executors.newCachedThreadPool(
            ThreadFactoryBuilder()
                .setNameFormat("install-product-with-dataset-${config.name}-%d")
                .build()
        )

        try {
            val unpackedProductFuture = executor.submitWithLogContext("install product") {
                ssh.newConnection().use { connection ->
                    productDistribution.install(connection, ".")
                }
            }

            val jiraHomeFuture = executor.submitWithLogContext("download Jira home") {
                ssh.newConnection().use { connection ->
                    jiraHomeSource.download(connection)
                }
            }

            val jiraHome = jiraHomeFuture.get()
            val unpackedProduct = unpackedProductFuture.get()
            ssh.newConnection().use { connection ->
                replaceDbconfigUrl(connection, "$jiraHome/dbconfig.xml")
                SetenvSh(unpackedProduct).setup(
                    connection = connection,
                    config = config,
                    gcLog = JiraGcLog(unpackedProduct),
                    jiraIp = ssh.host.ipAddress
                )
                connection.execute("echo jira.home=`realpath $jiraHome` > $unpackedProduct/atlassian-jira/WEB-INF/classes/jira-application.properties")
                connection.execute("echo jira.autoexport=false > $jiraHome/jira-config.properties")
                downloadMysqlConnector(
                    "https://dev.mysql.com/get/Downloads/Connector-J/mysql-connector-java-5.1.40.tar.gz",
                    connection
                )
                connection.execute("tar -xzf mysql-connector-java-5.1.40.tar.gz")
                connection.execute("cp mysql-connector-java-5.1.40/mysql-connector-java-5.1.40-bin.jar $unpackedProduct/lib")
                AwsCli().download(pluginsTransport.location, connection, target = "$jiraHome/plugins/installed-plugins")

                jdk.install(connection)
                val osMetrics = ubuntu.metrics(connection)

                config.splunkForwarder.jsonifyLog4j(
                    connection,
                    log4jPropertiesPath = "$unpackedProduct/atlassian-jira/WEB-INF/classes/log4j.properties"
                )
                config.splunkForwarder.run(connection, name, "/home/ubuntu/jirahome/log")
                config.profiler.install(connection)
                logger.info("$name is set up")

                return StandaloneStoppedNode(
                    name = name,
                    jiraHome = jiraHome,
                    analyticLogs = jiraHome,
                    resultsTransport = resultsTransport,
                    unpackedProduct = unpackedProduct,
                    osMetrics = osMetrics,
                    ssh = ssh,
                    launchTimeouts = config.launchTimeouts,
                    jdk = jdk,
                    profiler = config.profiler
                )
            }
        } finally {
            executor.shutdownNow()
        }
    }

    private fun replaceDbconfigUrl(
        connection: SshConnection,
        dbconfigXml: String
    ) {
        Sed().replace(
            connection = connection,
            expression = "(<url>.*(@(//)?|//))" + "([^:/]+)" + "(.*</url>)",
            output = """\1$databaseIp\5""",
            file = dbconfigXml
        )
    }

    private fun mountEphemeralDrive(
        connection: SshConnection
    ) {
        connection.execute("sudo tar -cf /home/ubuntu.tar .")
        connection.execute("sudo mkfs.ext4 /dev/nvme1n1")
        connection.execute("sudo mount -t ext4 /dev/nvme1n1 /home/ubuntu")
        connection.execute("sudo chown ubuntu /home/ubuntu")
        connection.execute("cd /home/ubuntu")
        connection.execute("tar -xf /home/ubuntu.tar")
        connection.execute("sudo rm /home/ubuntu.tar")
    }

    private fun downloadMysqlConnector(
        url: String,
        connection: SshConnection
    ) {
        IdempotentAction(
            description = "Download MySQL connector",
            action = {
                connection.execute("wget -q $url")
            }
        ).retry(
            maxAttempts = 3,
            backoff = StaticBackoff(Duration.ofSeconds(5))
        )
    }
}

private class StaticBackoff(
    private val backOff: Duration
) : Backoff {
    override fun backOff(attempt: Int): Duration = backOff
}
