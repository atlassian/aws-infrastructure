package com.atlassian.performance.tools.awsinfrastructure.jira

import com.atlassian.performance.tools.aws.Storage
import com.atlassian.performance.tools.awsinfrastructure.AwsCli
import com.atlassian.performance.tools.awsinfrastructure.storage.ApplicationStorage
import com.atlassian.performance.tools.infrastructure.api.Sed
import com.atlassian.performance.tools.infrastructure.api.jira.JiraGcLog
import com.atlassian.performance.tools.infrastructure.api.jira.JiraHomeSource
import com.atlassian.performance.tools.infrastructure.api.jira.JiraNodeConfig
import com.atlassian.performance.tools.infrastructure.api.jira.SetenvSh
import com.atlassian.performance.tools.infrastructure.api.jvm.JavaDevelopmentKit
import com.atlassian.performance.tools.infrastructure.api.jvm.OracleJDK
import com.atlassian.performance.tools.infrastructure.api.os.Ubuntu
import com.atlassian.performance.tools.ssh.Ssh
import com.atlassian.performance.tools.ssh.SshConnection
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.time.Duration

internal class StandaloneNodeFormula(
    private val jiraHomeSource: JiraHomeSource,
    private val pluginsTransport: Storage,
    private val resultsTransport: Storage,
    private val databaseIp: String,
    private val application: ApplicationStorage,
    private val ssh: Ssh,
    private val config: JiraNodeConfig
) : NodeFormula {
    private val logger: Logger = LogManager.getLogger(this::class.java)
    private val jdk: JavaDevelopmentKit = OracleJDK()
    private val ubuntu: Ubuntu = Ubuntu()

    override val name = config.name

    override fun provision(): StandaloneStoppedNode {
        logger.info("Setting up $name...")

        ssh.newConnection().use { connection ->
            val jiraArchiveName = application.download(connection, ".")
            val jiraHome = jiraHomeSource.download(connection)
            val unpackedProduct = getUnpackedProductName(connection, jiraArchiveName)

            replaceDbconfigUrl(connection, "$jiraHome/dbconfig.xml")
            connection.execute("tar -xzf $jiraArchiveName", Duration.ofMinutes(1))
            SetenvSh(unpackedProduct).setup(
                connection = connection,
                config = config,
                gcLog = JiraGcLog(unpackedProduct),
                jiraIp = ssh.host.ipAddress
            )
            connection.execute("echo jira.home=`realpath $jiraHome` > $unpackedProduct/atlassian-jira/WEB-INF/classes/jira-application.properties")
            connection.execute("echo jira.autoexport=false > $jiraHome/jira-config.properties")
            connection.execute("wget -q https://dev.mysql.com/get/Downloads/Connector-J/mysql-connector-java-5.1.40.tar.gz")
            connection.execute("tar -xzf mysql-connector-java-5.1.40.tar.gz")
            connection.execute("cp mysql-connector-java-5.1.40/mysql-connector-java-5.1.40-bin.jar $unpackedProduct/lib")
            AwsCli().download(pluginsTransport.location, connection, target = "$jiraHome/plugins/installed-plugins")

            jdk.install(connection)
            val osMetrics = ubuntu.metrics(connection)

            config.splunkForwarder.jsonifyLog4j(
                connection,
                log4jPropertiesPath = "$unpackedProduct/atlassian-jira/WEB-INF/classes/log4j.properties"
            )
            config.splunkForwarder.run(connection, name)


            logger.info("$name is set up")

            return StandaloneStoppedNode(
                name = name,
                jiraHome = jiraHome,
                analyticLogs = jiraHome,
                resultsTransport = resultsTransport,
                unpackedProduct = unpackedProduct,
                osMetrics = osMetrics,
                ssh = ssh
            )
        }
    }

    private fun getUnpackedProductName(
        connection: SshConnection,
        archiveName: String
    ): String {
        return connection
            .execute("tar -tf $archiveName | head -n 1", timeout = Duration.ofMinutes(1))
            .output
            .split("/")
            .first()
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
}