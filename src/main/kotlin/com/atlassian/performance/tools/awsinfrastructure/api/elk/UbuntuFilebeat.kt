package com.atlassian.performance.tools.awsinfrastructure.api.elk

import com.atlassian.performance.tools.infrastructure.api.os.Ubuntu
import com.atlassian.performance.tools.ssh.api.SshConnection
import java.io.File
import java.net.URI
import java.nio.file.Paths
import java.time.Duration

class UbuntuFilebeat(
    private val kibana: Kibana,
    private val configFile: File,
    private val supportingFiles: List<File>,
    private val fields: Map<String, Any>
) {
    private val exe = "filebeat"
    private val debFile = "$exe-7.3.1-amd64.deb"
    private val rootDownloadUri = URI("https://artifacts.elastic.co/downloads/beats/$exe/")

    fun install(ssh: SshConnection) {
        downloadAndInstall(ssh)

        configure(ssh)

        setup(ssh)

        start(ssh)
    }

    private fun downloadAndInstall(ssh: SshConnection) {
        Ubuntu().install(ssh, listOf("wget"))
        ssh.execute("wget ${rootDownloadUri.resolve(debFile)} -q --server-response")
        ssh.execute("sudo dpkg -i $debFile", Duration.ofSeconds(50))
    }

    private fun configure(ssh: SshConnection) {

        val config = ElasticConfig(exe).clean(ssh)

        // overwrite the existing file
        uploadFile(configFile, ssh, config)

        // upload supporting files to the home directory
        supportingFiles.forEach { uri ->
            uploadFile(uri, ssh, config)
        }
        appendDynamicConfig(config, ssh)
        //validate config
        validate(config, ssh)
    }

    private fun uploadFile(localFile: File, ssh: SshConnection, config: ElasticConfig) {
        val fileName = localFile.name
        val remoteTmpPath = Paths.get("/tmp/", fileName).toString()
        val remotePath = Paths.get(config.pathHome, fileName).toString()
        ssh.upload(localFile, remoteTmpPath)
        ssh.execute("sudo cp $remoteTmpPath $remotePath")
    }

    private fun appendDynamicConfig(
        config: ElasticConfig,
        ssh: SshConnection
    ) {
        config.append("setup.elk.host: '${kibana.address}'", ssh)
        val hostsYaml = kibana
            .elasticsearchHosts
            .map { it.toString() }
            .let { config.toYamlArray(it) }
        config.append("output.elasticsearch.hosts: $hostsYaml", ssh)
        config.append("fields: ${config.toYamlDictionary(fields)}", ssh)
    }

    private fun validate(config: ElasticConfig, ssh: SshConnection) {
        ssh.execute("sudo $exe test config -c ${config.configFilePath}")
    }

    private fun setup(ssh: SshConnection) {
        ssh.execute("sudo $exe setup --dashboards", Duration.ofSeconds(70))
    }

    private fun start(ssh: SshConnection) {
        ssh.execute("sudo service $exe start")
    }

    companion object {
        val FILEBEAT_VU_CONFIG_RESOURCE_PATH = "/elk/filebeat/vu/actionmetrics/filebeat.yml"

        val FILEBEAT_VU_SUPPORTING_RESOURCE_PATH = listOf(
            "/elk/filebeat/vu/actionmetrics/filebeat-processor-script-parseDuration.js",
            "/elk/filebeat/vu/actionmetrics/fields-actionmetrics.yml"
        )
    }
}
