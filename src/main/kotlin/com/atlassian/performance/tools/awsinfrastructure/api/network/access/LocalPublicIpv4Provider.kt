package com.atlassian.performance.tools.awsinfrastructure.api.network.access

import com.atlassian.performance.tools.awsinfrastructure.Ipv4Validator
import com.atlassian.performance.tools.awsinfrastructure.VerboseUriReader
import java.net.URI
import java.util.function.Supplier

/**
 * Provides public IPv4 address for "local" computer, which is the machine this code is executed on.
 */
class LocalPublicIpv4Provider private constructor(
    private val servicesToQuery: List<URI>,
    private val uriReader: (serviceUri: URI) -> String?,
    private val ipValidator: (ip: String) -> Boolean
) : () -> String, Supplier<String> {
    object Defaults {
        val servicesToQuery: List<URI> = listOf(
            URI("https://checkip.amazonaws.com"),
            URI("https://ifconfig.me/ip"),
            URI("https://api.ipify.org")
        )
        val uriReader: (serviceUri: URI) -> String? = VerboseUriReader()
        val ipValidator: (ip: String) -> Boolean = Ipv4Validator()
    }

    constructor() : this(
        servicesToQuery = Defaults.servicesToQuery,
        uriReader = Defaults.uriReader,
        ipValidator = Defaults.ipValidator
    )

    override fun invoke() = get()

    override fun get() = servicesToQuery
        .asSequence()
        .mapNotNull { serviceUri -> uriReader(serviceUri)?.trim() }
        .filter { it.isNotEmpty() }
        .filter { ipValidator(it) }
        .toSet()
        .let { set ->
            if (set.isEmpty()) {
                val services = servicesToQuery.joinToString { it.toString() }
                throw IllegalStateException(
                    "Queried services <$services> didn't report any valid public IP for this machine. " +
                        "You can override used services in the aws-infrastructure module API."
                )
            }
            if (set.size != 1) {
                val services = servicesToQuery.joinToString { it.toString() }
                throw IllegalStateException(
                    "Queried services <$services> reported different public IPs for this machine: ${set.toList()}. " +
                        "You should verify if your outbound network is configured correctly."
                )
            }
            set.first()
        }

    class Builder {
        private var servicesToQuery: MutableList<URI> = Defaults.servicesToQuery.toMutableList()
        private var uriReader: (serviceUri: URI) -> String? = Defaults.uriReader
        private var ipValidator: (ip: String) -> Boolean = Defaults.ipValidator

        fun servicesToQuery(servicesToQuery: List<URI>) = apply { this.servicesToQuery = servicesToQuery.toMutableList() }
        fun serviceToQuery(serviceUri: URI) = apply { this.servicesToQuery.add(serviceUri) }
        fun uriReader(uriReader: (serviceUri: URI) -> String?) = apply { this.uriReader = uriReader }
        fun ipValidator(ipValidator: (ip: String) -> Boolean) = apply { this.ipValidator = ipValidator }

        fun build() = LocalPublicIpv4Provider(
            servicesToQuery = servicesToQuery,
            uriReader = uriReader,
            ipValidator = ipValidator
        )
    }
}
