package com.atlassian.performance.tools.awsinfrastructure

import com.atlassian.performance.tools.workspace.api.TaskWorkspace
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.core.appender.ConsoleAppender
import org.apache.logging.log4j.core.appender.FileAppender
import org.apache.logging.log4j.core.config.AbstractConfiguration
import org.apache.logging.log4j.core.config.ConfigurationSource
import org.apache.logging.log4j.core.config.LoggerConfig
import org.apache.logging.log4j.core.layout.PatternLayout
import java.nio.file.Path
import java.nio.file.Paths

internal class LogConfiguration(
    private val workspace: TaskWorkspace
) : AbstractConfiguration(null, ConfigurationSource.NULL_SOURCE) {

    override fun doConfigure() {
        listOf(
            logToFile(
                name = "com.atlassian.performance.tools.awsinfrastructure",
                path = Paths.get("aws-infra.log")
            ).also { log ->
                log.addAppender(
                    KConsoleAppenderBuilder()
                        .setName("console")
                        .setLayout(layout("%d{ABSOLUTE} %highlight{%-5level} %x %msg%n"))
                        .build(),
                    Level.INFO,
                    null
                )
            },
            logToFile(
                name = "com.atlassian.performance.tools.ssh",
                path = Paths.get("ssh.log")
            ),
            logToFile(
                name = "com.atlassian.performance.tools.aws.api.SshKeyFile",
                path = Paths.get("ssh-login.log")
            ),
            logToFile(
                name = "com.atlassian.performance.tools",
                path = Paths.get("detailed.log")
            )
        ).forEach { addLogger(it.name, it) }
    }

    private fun logToFile(
        name: String,
        path: Path,
        pattern: String = "%d{ISO8601}{UTC}Z %-5level <%t> %x [%logger] %msg%n"
    ): LoggerConfig {
        val log = LoggerConfig(
            name,
            Level.DEBUG,
            true
        )
        val absolutePath = workspace
            .directory
            .resolve(path)
            .toAbsolutePath()
        log.addAppender(
            KFileAppenderBuilder()
                .setName(absolutePath.fileName.toString())
                .setLayout(layout(pattern))
                .withFileName(absolutePath.toString())
                .withAppend(true)
                .build(),
            Level.DEBUG,
            null
        )
        return log
    }

    private fun layout(
        pattern: String
    ): PatternLayout = PatternLayout.newBuilder()
        .withPattern(pattern)
        .withConfiguration(this)
        .build()
}

private class KFileAppenderBuilder : FileAppender.Builder<KFileAppenderBuilder>()
private class KConsoleAppenderBuilder : ConsoleAppender.Builder<KConsoleAppenderBuilder>()
