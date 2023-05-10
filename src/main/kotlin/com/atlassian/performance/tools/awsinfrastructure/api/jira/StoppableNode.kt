package com.atlassian.performance.tools.awsinfrastructure.api.jira

import com.atlassian.performance.tools.ssh.api.Ssh
import com.atlassian.performance.tools.ssh.api.SshHost
import java.time.Duration
import javax.json.Json
import javax.json.JsonObject

class StoppableNode private constructor(
    private val jiraPath: String,
    private val ssh: Ssh
) {
    fun stop() {
        ssh.newConnection().use { ssh ->
            ssh.execute("source ~/.profile; ${jiraPath}/bin/stop-jira.sh", Duration.ofMinutes(3))
        }
    }

    fun toJson(): JsonObject {
        return Json.createObjectBuilder()
            .add("jiraPath", jiraPath)
            .add("ssh", ssh.host.toJson())
            .build()
    }

    class Builder(
        private var jiraPath: String,
        private var ssh: Ssh
    ) {
        constructor(json: JsonObject) : this(
            jiraPath = json.getString("jiraPath"),
            ssh = Ssh(
                SshHost(json.getJsonObject("ssh"))
            )
        )

        fun jiraPath(jiraPath: String) = apply { this.jiraPath = jiraPath }
        fun ssh(ssh: Ssh) = apply { this.ssh = ssh }
        fun build() = StoppableNode(
            jiraPath = jiraPath,
            ssh = ssh
        )
    }
}
