package com.atlassian.performance.tools.awsinfrastructure.api.aws

import org.assertj.core.api.Assertions
import org.junit.Test

class AwsCliTest {
    @Test
    fun defaultVersionIs2_9_12() {
        Assertions.assertThat(AwsCli().cliVersion).isEqualTo("2.9.12")
    }

    @Test
    fun constructsWithValidVersions() {
        AwsCli("0.0.0").cliVersion
        AwsCli("1.2.3").cliVersion
        AwsCli("2.3.4").cliVersion
        AwsCli("4.0.3").cliVersion
    }

    @Test
    fun constructsWithInValidVersionsThrows() {
        Assertions.assertThatThrownBy { AwsCli("") }
        Assertions.assertThatThrownBy { AwsCli(".2.3") }
        Assertions.assertThatThrownBy { AwsCli("1.?.3") }
        Assertions.assertThatThrownBy { AwsCli("1.4.0dev") }
        Assertions.assertThatThrownBy { AwsCli("1.4.⅑") }
        Assertions.assertThatThrownBy { AwsCli("1.2.3; echo \"hi\";") }
    }
}
