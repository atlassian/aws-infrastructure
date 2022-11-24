package com.atlassian.performance.tools.awsinfrastructure

import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.net.URI

internal class VerboseUriReader : (URI) -> String? {
    private val logger: Logger = LogManager.getLogger(this::class.java)

    override fun invoke(
        serviceUri: URI
    ) = try {
        serviceUri.toURL()
            .also { logger.debug("Querying $it") }
            .readText()
            .also { logger.debug("Got \"$it\" from $serviceUri") }
    } catch (e: Exception) {
        logger.debug("Failed when querying $serviceUri", e)
        null
    }
}