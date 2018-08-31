package com.atlassian.performance.tools.awsinfrastructure

import com.atlassian.performance.tools.infrastructure.api.jira.JiraNodeConfig
import com.atlassian.performance.tools.io.api.readResourceText
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator

// This is a workaround for JPT-296. Please use EC2 API instead of template hacking when fixed.
internal class TemplateBuilder(baseTemplateName: String) {
    private val template = readResourceText("aws/$baseTemplateName").replace("!Ref", "__Ref__")

    private val mapper = ObjectMapper(YAMLFactory()
        .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES)
        .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER))
    private val mappedTemplate = mapper.readTree(template)

    fun adaptTo(
        configs: List<JiraNodeConfig>
    ): String {
        // HACK: we open all the ports for all the nodes, because they all reuse the same security group
        val allPorts = configs
            .flatMap { it.debug.getRequiredPorts() + it.splunkForwarder.getRequiredPorts() + it.remoteJmx.getRequiredPorts() }
            .toSet()
        addSecurityGroupIngress(allPorts)
        setNodesCount(configs.size)
        setDescription("Serves ${configs.size}-node JIRA Data Center.")
        return build()
    }

    fun addSecurityGroupIngress(
        ports: Set<Int>,
        securityGroupName: String = "TomcatSecurityGroup"
    ): TemplateBuilder = apply {
        val ingresses = mappedTemplate
            .get("Resources")
            .get(securityGroupName)
            .get("Properties")
            .get("SecurityGroupIngress") as ArrayNode
        for (port in ports) {
            ingresses
                .addObject()
                .put("IpProtocol", "tcp")
                .put("FromPort", port)
                .put("ToPort", port)
                .put("CidrIp", "0.0.0.0/0")
        }
    }

    fun setNodesCount(
        desiredNodesCount: Int
    ): TemplateBuilder = apply {
        val existingNodes: MutableList<String> = mutableListOf()
        val resources = mappedTemplate.get("Resources") as ObjectNode

        for (resource in resources.fieldNames()) {
            if (resource.startsWith("jira", ignoreCase = true)) {
                existingNodes.add(resource)
            }
        }
        if (existingNodes.count() == desiredNodesCount) {
            return this
        }

        val nodeTemplate = resources.get(existingNodes[0])
        for (existingNode in existingNodes) {
            resources.remove(existingNode)
        }
        for (nodeNumber in 1..desiredNodesCount) {
            resources.set("jira$nodeNumber", nodeTemplate)
        }
    }

    fun setDescription(description: String) = apply {
        (mappedTemplate as ObjectNode).put("Description", description)
    }

    fun build(): String {
        val result = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(mappedTemplate)
        return result.replace("__Ref__", "!Ref")
    }
}