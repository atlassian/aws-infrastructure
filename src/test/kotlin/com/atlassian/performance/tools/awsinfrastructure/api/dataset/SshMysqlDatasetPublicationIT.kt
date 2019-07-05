package com.atlassian.performance.tools.awsinfrastructure.api.dataset

import com.atlassian.performance.tools.awsinfrastructure.IntegrationTestRuntime
import com.atlassian.performance.tools.awsinfrastructure.api.AwsDatasetModification
import com.atlassian.performance.tools.awsinfrastructure.api.DatasetCatalogue
import org.junit.Test

class SshMysqlDatasetPublicationIT {

    private val aws = IntegrationTestRuntime.aws

    @Test
    fun shouldPrepareSmallDatasetPublication() {
        SshMysqlDatasetPublication().preparePublication(
            AwsDatasetModification.Builder(
                aws,
                DatasetCatalogue().smallJiraSeven()
            )
        )
    }
}