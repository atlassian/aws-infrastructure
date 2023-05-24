package com.atlassian.performance.tools.awsinfrastructure

import com.atlassian.performance.tools.infrastructure.api.jira.JiraNodeConfig
import com.atlassian.performance.tools.io.api.readResourceText
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.util.function.Function


// This is a workaround for JPT-296. Please use EC2 API instead of template hacking when fixed.
internal class TemplateBuilder(
    private val baseTemplateName: String
) {
    private val logger: Logger = LogManager.getLogger(this::class.java)
    private val template = readResourceText("aws/$baseTemplateName").replace("!Ref", "__Ref__")

    private val mapper = ObjectMapper(
        YAMLFactory()
            .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES)
            .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
    )
    private val mappedTemplate = mapper.readTree(template)

    fun adaptTo(
        configs: List<JiraNodeConfig>
    ): String {
        setNodesCount(configs.size)
        replaceDescription(Function { oldDescription ->
            oldDescription
                ?.replace(Regex("\\d node"), "${configs.size} node")
                ?: "Serves a ${configs.size} node Jira without a load balancer"
        })
        return build()
    }

    fun setNodesCount(
        desiredNodesCount: Int
    ): TemplateBuilder = apply {
        val existingNodes: MutableList<String> = mutableListOf()
        val resources = mappedTemplate.get("Resources") as ObjectNode

        for (resource in resources.fieldNames()) {
            if (resource.startsWith("jira", ignoreCase = true) &&
                resources.get(resource).get("Type").asText() == "AWS::EC2::Instance"
            ) {
                logger.debug("Existing Jira node in $baseTemplateName: $resource")
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

    private fun replaceDescription(
        descriptionTransform: Function<String?, String>
    ): TemplateBuilder = apply {
        (mappedTemplate as ObjectNode).let {
            val currentDescription = when {
                it.has("Description") -> it.get("Description").asText()
                else -> null
            }
            it.put("Description", descriptionTransform.apply(currentDescription))
        }
    }

    override fun toString() = mapper
        .writerWithDefaultPrettyPrinter()
        .writeValueAsString(mappedTemplate)
        .replace("__Ref__", "!Ref")

    fun build() = toString()
        .also { logger.debug("Transformed $baseTemplateName into: \n$it") }
}
