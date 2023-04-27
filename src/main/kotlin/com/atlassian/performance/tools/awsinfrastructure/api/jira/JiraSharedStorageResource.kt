package com.atlassian.performance.tools.awsinfrastructure.api.jira;

/**
 * Describes a set of resources used by Jira and stored in shared storage (eg. shared home or S3).
 */
enum class JiraSharedStorageResource {
    AVATARS, ATTACHMENTS
}
