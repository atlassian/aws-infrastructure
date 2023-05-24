package com.atlassian.performance.tools.awsinfrastructure.api.network.access

import org.hamcrest.Matchers.equalTo
import org.junit.Assert.assertThat
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI
import java.util.function.Function
import java.util.function.Predicate

class LocalPublicIpv4ProviderTest {

    @Test
    fun providesIpWhenAllServicesReturnTheSameIps() {
        // given
        val servicesAndTheirResponses = mapOf(
            URI("http://localhost/1") to "1.2.3.4",
            URI("http://localhost/2") to "1.2.3.4",
            URI("http://localhost/3") to "1.2.3.4"
        )
        val localPublicIpv4Provider = LocalPublicIpv4Provider.Builder()
            .servicesToQuery(servicesAndTheirResponses.keys.toList())
            .uriReader(Function { serviceUri: URI -> servicesAndTheirResponses[serviceUri] })
            .ipValidator(Predicate { true })
            .build()

        // when
        val localPublicIp = localPublicIpv4Provider.get()

        // then
        assertThat(localPublicIp, equalTo("1.2.3.4"))
    }

    @Test
    fun providesIpWhenOnlySomeServicesFail() {
        // given
        val servicesAndTheirResponses = mapOf(
            URI("http://localhost/1") to null,
            URI("http://localhost/2") to "1.2.3.4",
            URI("http://localhost/3") to "1.2.3.4"
        )
        val localPublicIpv4Provider = LocalPublicIpv4Provider.Builder()
            .servicesToQuery(servicesAndTheirResponses.keys.toList())
            .uriReader(Function { serviceUri: URI -> servicesAndTheirResponses[serviceUri] })
            .ipValidator(Predicate { true })
            .build()

        // when
        val localPublicIp = localPublicIpv4Provider.get()

        // then
        assertThat(localPublicIp, equalTo("1.2.3.4"))
    }

    @Test
    fun failsToProvideIpWhenServicesReturnDifferentIps() {
        // given
        val servicesAndTheirResponses = mapOf(
            URI("http://localhost/1") to "1.2.3.4",
            URI("http://localhost/2") to "2.3.4.5",
            URI("http://localhost/3") to "1.2.3.4"
        )
        val localPublicIpv4Provider = LocalPublicIpv4Provider.Builder()
            .servicesToQuery(servicesAndTheirResponses.keys.toList())
            .uriReader(Function { serviceUri: URI -> servicesAndTheirResponses[serviceUri] })
            .ipValidator(Predicate { true })
            .build()

        // when
        var exceptionHappened = false
        try {
            localPublicIpv4Provider.get()
        } catch (e: IllegalStateException) {
            exceptionHappened = true
        }

        // then
        assertTrue(exceptionHappened)
    }

    @Test
    fun failsToProvideIpWhenServicesDontReturnAnyIp() {
        // given
        val servicesAndTheirResponses = mapOf(
            URI("http://localhost/1") to null,
            URI("http://localhost/2") to null,
            URI("http://localhost/3") to null
        )
        val localPublicIpv4Provider = LocalPublicIpv4Provider.Builder()
            .servicesToQuery(servicesAndTheirResponses.keys.toList())
            .uriReader(Function { serviceUri: URI -> servicesAndTheirResponses[serviceUri] })
            .ipValidator(Predicate { true })
            .build()

        // when
        var exceptionHappened = false
        try {
            localPublicIpv4Provider.get()
        } catch (e: IllegalStateException) {
            exceptionHappened = true
        }

        // then
        assertTrue(exceptionHappened)
    }
}
