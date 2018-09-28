package com.atlassian.performance.tools.awsinfrastructure

import com.atlassian.performance.tools.workspace.api.TaskWorkspace
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.core.appender.ConsoleAppender
import org.apache.logging.log4j.core.appender.FileAppender
import org.apache.logging.log4j.core.config.AbstractConfiguration
import org.apache.logging.log4j.core.config.ConfigurationSource
import org.apache.logging.log4j.core.config.LoggerConfig
import org.apache.logging.log4j.core.layout.PatternLayout

internal class LogConfiguration(
    private val workspace: TaskWorkspace
) : AbstractConfiguration(null, ConfigurationSource.NULL_SOURCE) {
    override fun doConfigure() {
        val loggerConfig = LoggerConfig(
            "com.atlassian.performance.tools.awsinfrastructure",
            Level.DEBUG,
            false
        )
        val logPath = workspace.directory.resolve("aws-infra.log")
        loggerConfig.addAppender(
            KFileAppenderBuilder()
                .withName("file")
                .withLayout(layout("%d{ISO8601}{UTC}Z %-5level %x [%logger{1}] %msg%n"))
                .withFileName(logPath.toAbsolutePath().toString())
                .withAppend(false)
                .build(),
            Level.DEBUG,
            null
        )
        loggerConfig.addAppender(
            KConsoleAppenderBuilder()
                .withName("console")
                .withLayout(layout("%d{ABSOLUTE} %highlight{%-5level} %x %msg%n"))
                .build(),
            Level.INFO,
            null
        )
        addLogger(loggerConfig.name, loggerConfig)
    }

    private fun layout(pattern: String): PatternLayout {
        return PatternLayout.newBuilder()
            .withPattern(pattern)
            .withConfiguration(this)
            .build()
    }
}

private class KFileAppenderBuilder : FileAppender.Builder<KFileAppenderBuilder>()
private class KConsoleAppenderBuilder : ConsoleAppender.Builder<KConsoleAppenderBuilder>()