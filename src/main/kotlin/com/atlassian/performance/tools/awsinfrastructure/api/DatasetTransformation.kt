package com.atlassian.performance.tools.awsinfrastructure.api

/**
 * A custom transformation which can be applied to dataset
 */
interface DatasetTransformation {

    fun transform(infrastructure: Infrastructure<*>)
}
