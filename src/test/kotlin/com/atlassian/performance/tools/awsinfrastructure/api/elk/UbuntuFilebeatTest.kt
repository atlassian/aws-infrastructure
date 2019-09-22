package com.atlassian.performance.tools.awsinfrastructure.api.elk

import com.atlassian.performance.tools.awsinfrastructure.mock.RememberingSshConnection
import org.junit.Test
import java.net.URI
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.util.Files
import java.nio.file.Paths

class UbuntuFilebeatTest {
    @Test
    fun shouldDownloadConfigureAndStartDuringCallToInstall() {
        val kibana = Kibana(URI("example.com:5601"), listOf(URI("example.com:9200")))

        val temporaryFolder = Files.newTemporaryFolder()
        val ufb = UbuntuFilebeat(
            kibana,
            UbuntuFilebeat.FILEBEAT_VU_CONFIG_RESOURCE_PATH.let { resourcePath ->
                UbuntuFilebeat::class.java.getResourceAsStream(resourcePath).use { resourceStream ->
                    temporaryFolder.resolve(Paths.get(resourcePath).fileName.toString()).also { file ->
                        file.outputStream().use {
                            resourceStream.transferTo(it)
                        }
                    }
                }
            },
            UbuntuFilebeat.FILEBEAT_VU_SUPPORTING_RESOURCE_PATH.map { resourcePath ->
                UbuntuFilebeat::class.java.getResourceAsStream(resourcePath).use { resourceStream ->
                    temporaryFolder.resolve(Paths.get(resourcePath).fileName.toString()).also { file ->
                        file.outputStream().use {
                            resourceStream.transferTo(it)
                        }
                    }
                }
            },
            fields = emptyMap()
        )
        val ssh = RememberingSshConnection()

        val expectedUploads = listOf(
            "$temporaryFolder/filebeat.yml -> /tmp/filebeat.yml",
            "$temporaryFolder/filebeat-processor-script-parseDuration.js -> /tmp/filebeat-processor-script-parseDuration.js",
            "$temporaryFolder/fields-actionmetrics.yml -> /tmp/fields-actionmetrics.yml"
        )

        val expectedCommands = listOf(
            // boilerplate (possibly too fragile for this test?)
            "sudo rm -rf /var/lib/apt/lists/*",
            "sudo apt-get update -qq",
            "sudo DEBIAN_FRONTEND=noninteractive apt-get install -qq wget",

            // download and install
            "wget https://artifacts.elastic.co/downloads/beats/filebeat/filebeat-7.3.1-amd64.deb -q --server-response",
            "sudo dpkg -i filebeat-7.3.1-amd64.deb",

            // configure
            "[ -f /etc/filebeat/filebeat.yml ] && sudo mv /etc/filebeat/filebeat.yml /etc/filebeat/filebeat.yml.orig",
            "sudo touch /etc/filebeat/filebeat.yml",

            // uploads to tmp happen now...

            // cp tmp files to filebeat pathHome
            "sudo cp /tmp/filebeat.yml /etc/filebeat/filebeat.yml",
            "sudo cp /tmp/filebeat-processor-script-parseDuration.js /etc/filebeat/filebeat-processor-script-parseDuration.js",
            "sudo cp /tmp/fields-actionmetrics.yml /etc/filebeat/fields-actionmetrics.yml",
            "echo \"setup.kibana.host: 'example.com:5601'\" | sudo tee -a /etc/filebeat/filebeat.yml",
            "echo \"output.elasticsearch.hosts: ['example.com:9200']\" | sudo tee -a /etc/filebeat/filebeat.yml",
            "echo \"fields: {}\" | sudo tee -a /etc/filebeat/filebeat.yml",

            // validate
            "sudo filebeat export config -c /etc/filebeat/filebeat.yml",
            "sudo filebeat test config -c /etc/filebeat/filebeat.yml",

            // start
            "sudo filebeat setup --dashboards",
            "sudo service filebeat start"
        )

        ufb.install(ssh)

        assertThat(ssh.commands)
            .containsExactlyElementsOf(
                expectedCommands
            )

        assertThat(ssh.uploads.map { "${it.localSource} -> ${it.remoteDestination}" })
            .containsExactlyElementsOf(
                expectedUploads
            )
    }
}
