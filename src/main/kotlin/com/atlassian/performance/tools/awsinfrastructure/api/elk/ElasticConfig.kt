package com.atlassian.performance.tools.awsinfrastructure.api.elk

import com.atlassian.performance.tools.ssh.api.SshConnection

class ElasticConfig(
    private val elasticProduct: String,
    val pathHome: String = "/etc/$elasticProduct",
    val configFile: String = "$elasticProduct.yml",
    val configFilePath: String = "$pathHome/$configFile"
) {
    fun toYamlArray(
        elements: List<String>
    ): String {
        val wrappedElements = elements.joinToString(separator = ", ", prefix = "'", postfix = "'")
        return "[$wrappedElements]"
    }

    fun toYamlDictionary(
        map: Map<String, Any>
    ): String {
        val entries = map
            .map { (key, value) ->
                "\"$key\": ${toYamlValue(value)}"
            }
            .joinToString(separator = ", ")
        return "{$entries}"
    }

    private fun toYamlValue(
        value: Any
    ): String {
        return when (value) {
            is String -> "\"$value\""
            else -> value.toString()
        }
    }

    fun clean(shell: SshConnection) : ElasticConfig {
        shell.execute("[ -f $configFilePath ] && sudo mv $configFilePath $configFilePath.orig")
        shell.execute("sudo touch $configFilePath")
        return this
    }

    fun append(
        line: String,
        shell: SshConnection
    ) {
        val escapedLine = line.replace("\"", "\\\"")
        shell.execute("echo \"$escapedLine\" | sudo tee -a /etc/$elasticProduct/$elasticProduct.yml")
    }
}
