package com.atlassian.performance.tools.awsinfrastructure.api.jira

/**
 * Configuration for how and where to store data in a Jira DC cluster.
 */
class JiraSharedStorageConfig private constructor(
    val storeAvatarsInS3: Boolean,
    val storeAttachmentsInS3: Boolean
){

    fun isAnyResourceStoredInS3(): Boolean {
        return storeAvatarsInS3 || storeAttachmentsInS3
    }

    override fun toString(): String {
        return "JiraSharedStorageConfig(storeAvatarsInS3=$storeAvatarsInS3, storeAttachmentsInS3=$storeAttachmentsInS3)"
    }

    class Builder() {
        private var storeAvatarsInS3: Boolean = false
        private var storeAttachmentsInS3: Boolean = false

        constructor(
            jiraSharedStorageConfig: JiraSharedStorageConfig
        ) : this() {
            storeAvatarsInS3 = jiraSharedStorageConfig.storeAvatarsInS3
            storeAttachmentsInS3 = jiraSharedStorageConfig.storeAttachmentsInS3
        }

        fun storeAvatarsInS3(storeAvatarsInS3: Boolean) = apply { this.storeAvatarsInS3 = storeAvatarsInS3 }
        fun storeAttachmentsInS3(storeAttachmentsInS3: Boolean) = apply { this.storeAttachmentsInS3 = storeAttachmentsInS3 }

        fun build() = JiraSharedStorageConfig(
            storeAvatarsInS3 = storeAvatarsInS3,
            storeAttachmentsInS3 = storeAttachmentsInS3
        )
    }
}
