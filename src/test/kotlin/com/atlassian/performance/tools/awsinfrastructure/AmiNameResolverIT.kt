package com.atlassian.performance.tools.awsinfrastructure

import com.amazonaws.regions.Regions
import com.amazonaws.regions.Regions.*
import com.atlassian.performance.tools.aws.api.Aws
import org.hamcrest.Matchers.isEmptyString
import org.hamcrest.Matchers.not
import org.junit.Assert.assertThat
import org.junit.Test

class AmiNameResolverIT {
    @Test
    fun worksInUsEast1() {
        checkRegion(US_EAST_1)
    }

    @Test
    fun worksInUsEast2() {
        checkRegion(US_EAST_2)
    }

    @Test
    fun worksInUsWest1() {
        checkRegion(US_WEST_1)
    }

    @Test
    fun worksInUsWest2() {
        checkRegion(US_WEST_2)
    }

    @Test
    fun worksInEuWest1() {
        checkRegion(EU_WEST_1)
    }

    @Test
    fun worksInEuWest2() {
        checkRegion(EU_WEST_2)
    }

    @Test
    fun worksInEuWest3() {
        checkRegion(EU_WEST_3)
    }

    @Test
    fun worksInEuCentral1() {
        checkRegion(EU_CENTRAL_1)
    }

    @Test
    fun worksInEuNorth1() {
        checkRegion(EU_NORTH_1)
    }

    @Test
    fun worksInApSouth1() {
        checkRegion(AP_SOUTH_1)
    }

    @Test
    fun worksInApSouthEast1() {
        checkRegion(AP_SOUTHEAST_1)
    }

    @Test
    fun worksInApSouthEast2() {
        checkRegion(AP_SOUTHEAST_2)
    }

    @Test
    fun worksInApNorthEast1() {
        checkRegion(AP_SOUTHEAST_1)
    }

    @Test
    fun worksInApNorthEast2() {
        checkRegion(AP_SOUTHEAST_2)
    }

    private fun checkRegion(region: Regions) {
        val vuAmi = AmiNameResolver.vuAmi(
            Aws.Builder(region)
                .regionsWithHousekeeping(listOf(region)) // Not true, but it doesn't let us build the class without it.
                .build()
        )

        assertThat(vuAmi, not(isEmptyString()))
    }
}