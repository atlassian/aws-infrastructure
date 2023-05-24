package com.atlassian.performance.tools.awsinfrastructure.api.network.access

import com.atlassian.performance.tools.awsinfrastructure.Ipv4Validator
import com.atlassian.performance.tools.awsinfrastructure.VerboseUriReader
import java.net.URI
import java.util.function.Function
import java.util.function.Predicate
import java.util.function.Supplier

/**
 * Provides public IPv4 address for "local" computer, which is the machine this code is executed on.
 */
class LocalPublicIpv4Provider private constructor(
    private val servicesToQuery: List<URI>,
    private val uriReader: Function<URI, String?>,
    private val ipValidator: Predicate<String>
) : Supplier<String> {

    override fun get() = servicesToQuery
        .asSequence()
        .mapNotNull { serviceUri -> uriReader.apply(serviceUri)?.trim() }
        .filter { it.isNotEmpty() }
        .filter { ipValidator.test(it) }
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
        private var servicesToQuery: MutableList<URI> = mutableListOf(
            URI("https://checkip.amazonaws.com"),
            URI("https://ifconfig.me/ip"),
            URI("https://api.ipify.org")
        )
        private var uriReader: Function<URI, String?> = VerboseUriReader()
        private var ipValidator: Predicate<String> = Ipv4Validator()

        fun servicesToQuery(servicesToQuery: List<URI>) =
            apply { this.servicesToQuery = servicesToQuery.toMutableList() }

        fun serviceToQuery(serviceUri: URI) = apply { this.servicesToQuery.add(serviceUri) }
        fun uriReader(uriReader: Function<URI, String?>) = apply { this.uriReader = uriReader }
        fun ipValidator(ipValidator: Predicate<String>) = apply { this.ipValidator = ipValidator }

        fun build() = LocalPublicIpv4Provider(
            servicesToQuery = servicesToQuery,
            uriReader = uriReader,
            ipValidator = ipValidator
        )
    }
}
