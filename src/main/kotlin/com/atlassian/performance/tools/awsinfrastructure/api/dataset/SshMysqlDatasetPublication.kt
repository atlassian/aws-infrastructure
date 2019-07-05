package com.atlassian.performance.tools.awsinfrastructure.api.dataset

import com.atlassian.performance.tools.awsinfrastructure.api.AwsDatasetModification
import com.atlassian.performance.tools.infrastructure.api.database.LicenseOverridingMysql
import com.atlassian.performance.tools.infrastructure.api.dataset.Dataset
import org.apache.logging.log4j.LogManager

/**
 * Overwrites with licenses taken from "Timebomb licenses for testing server apps".
 * See [link](https://developer.atlassian.com/platform/marketplace/timebomb-licenses-for-testing-server-apps/).
 *
 * @since 2.12.0
 */
class SshMysqlDatasetPublication {

    private val logger = LogManager.getLogger(this::class.java)

    /**
     * Prepares the publication of [modification].
     * It doesn't actually publish it, that's still to be handled externally.
     */
    fun preparePublication(
        modification: AwsDatasetModification.Builder
    ) {
        val original = modification.dataset
        val publishableDatabase = LicenseOverridingMysql(
            original.database,
            listOf(licenseJswSafely(), licenseJsdSafely())
        )
        val publishableModification = modification
            .dataset(
                Dataset.Builder(original)
                    .database(publishableDatabase)
                    .build()
            )
            .build()
        val publishableDataset = publishableModification.modify()
        logger.info("$publishableDataset is publishable")
    }

    /**
     * 10 user Jira Software Data Center license, expires in 3 hours.
     */
    private fun licenseJswSafely(): String = """
        AAAB8w0ODAoPeNp9Uk2P2jAQvedXWOoNydmELVKLFKlL4u7SLglKQj+27cEkA3gb7GjssMu/rwnQl
        s9DDvHMvPfmvXmTN0BGfE08n3jdftfv927J/SgnXc9/58wRQC5UXQO6j6IAqYGVwgglAxbnLB2nw
        4w5cbOcAiaziQbUge85oZKGFybmSwjKmiMKvfjATcW1Fly6hVo64waLBdcQcQPBhot6Per5zo4lX
        9fQjofJaMTScHj3uC+x11rgup0b3z7sudiIi+oSWQa4AhxGweD+fU6/Tb68pZ+fnh7owPO/Os8Cu
        VujKpvCuJsfqtXMvHAE1+KKFQQGG3A+2cp412XJeQjSHLVkzVQXKOrWn/bljH/nNmslXPa30+nES
        U4/Jikdp0k0CfNhEtNJxmwhCBGsFSWZrolZANmhECYLVQISu9gzFIb8WBhT/+zf3MyVe2DOTbWdo
        LCd+OWSSBGpDCmFNiimjQGLLDQxihSNNmppU3Yd67c0ILksjhOxqsKU3eUsooPvG4kXUrli/MlF7
        dayEU7kb6lepJOxOLAf7XneFmkfCuCp95nh+LdwhfegL8E5l0LzNo4IVlApi0Vy0GZvs9O6b+vHZ
        xzBv0toB3Yuk5lCwuualHs8fSD0/3NqdZ48nBd+5bjYilfNdokZr6zmP7TmY5YwLAIUNq8MbmR8G
        faV9ulfLz1K+3g9j1YCFDeq7aYROMQbwMIvHimNt7/bJCCIX02nj
        """.trimIndent()

    /**
     * 10 user Jira Service Desk Data Center license, expires in 3 hours.
     */
    private fun licenseJsdSafely(): String = """
        AAAB3w0ODAoPeNqFUttu0zAYvs9TWOKuktOkbBJUisSamK2wHJQDhwEXrvN39Zbake109O1xk0ZjQ
        y0XvrD/w3fym7IDFNM98nzkzeaz2dy/QNdxiWae/8554Iq6rZJ1x4x7uGANascZ1KAfXcoM30FgV
        AdO0m1XoNJ1ZRt04HtOKIWxDQndQlC3VCmuNx+oaajWnAqXya1jj/v8cmpz1im2oRoiaiA4sMLeJ
        fZ859Y2Cw3lvoUeJEzjmOTh8up2LJHfLVf7fi57ezMyIjHlzSlKhSUBahkFi+v3Jf5WfbnAn+/ub
        vDC8786n6wB2dEMu5WGIIyCF2RPSzpD97zLZwaLbqWZ4q3hUgz4/apRfs37AklKkmf5siD/Qfonw
        8lkkqQl/pjmOMvTqArLZZrgqiC2EIQKrLM1Wu2R2QA6giIimKxBIYvyAMygHxtj2p/z6fReui+8n
        jbDBIZh4peLIomENKjm2ii+6gzYzVwjIxHrtJFbS8t1bHzCgKCCvQ7YsgpzclWSCC++HyieCPlI1
        aZciUchn4RTkCSwB1963uDRGC+owddU3VPBNe0NjWAHjWytyBK0GZU7vSG2/vqjRvAcUj9wFI7WU
        iHatqge9+kB/O8PRXa06QbYNW0syh8ZAEsLMCwCFGL057N7jgFszjOK1RP20LSXbPDkAhR/Kf0Zh
        AzWSddIoCukypG76CReHQ==X02mq
        """.trimIndent()
}