package dev.triumphteam.polaris.yaml.encoding

import dev.triumphteam.polaris.serialization.encoding.AbstractPolarisEncoder
import dev.triumphteam.polaris.serialization.encoding.invalidKeyKindException
import dev.triumphteam.polaris.serialization.encoding.listLike
import dev.triumphteam.polaris.serialization.encoding.objLike
import dev.triumphteam.polaris.serialization.encoding.polarisKind
import dev.triumphteam.polaris.yaml.Yaml
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.encoding.CompositeEncoder
import kotlinx.serialization.modules.SerializersModule
import org.snakeyaml.engine.v2.comments.CommentLine
import org.snakeyaml.engine.v2.comments.CommentType
import org.snakeyaml.engine.v2.common.FlowStyle
import org.snakeyaml.engine.v2.common.ScalarStyle
import org.snakeyaml.engine.v2.nodes.MappingNode
import org.snakeyaml.engine.v2.nodes.Node
import org.snakeyaml.engine.v2.nodes.NodeTuple
import org.snakeyaml.engine.v2.nodes.NodeType
import org.snakeyaml.engine.v2.nodes.ScalarNode
import org.snakeyaml.engine.v2.nodes.SequenceNode
import org.snakeyaml.engine.v2.nodes.Tag
import org.snakeyaml.engine.v2.representer.BaseRepresenter
import java.util.Optional

private const val CLASS_DISCRIMINATOR_TAG = "type"

internal abstract class AbstractYamlEncoder(
    private val yaml: Yaml,
    private val representation: BaseRepresenter,
    valueConsumer: (Node) -> Unit,
) : AbstractPolarisEncoder<Node, ConfigTag>(valueConsumer) {

    override val serializersModule: SerializersModule
        get() = yaml.serializersModule

    override fun createTag(name: String, comments: List<String>): ConfigTag {
        return ConfigTag(
            name = name,
            comments = comments.map { comment ->
                CommentLine(
                    Optional.empty(),
                    Optional.empty(),
                    comment,
                    if (comment.trim().isEmpty() || comment.trim() == "\n") {
                        CommentType.BLANK_LINE
                    } else {
                        CommentType.BLOCK
                    }
                )
            }
        )
    }

    override fun encodeTaggedValue(tag: ConfigTag, value: Any) =
        encodeTaggedConfigValue(tag, nodeValueOf(value))

    override fun encodeTaggedNull(tag: ConfigTag) {
        if (!yaml.explicitNulls) return
        encodeTaggedConfigValue(tag, nodeValueOf(null))
    }

    override fun encodeTaggedChar(tag: ConfigTag, value: Char) = encodeTaggedString(tag, value.toString())

    override fun shouldEncodeElementDefault(descriptor: SerialDescriptor, index: Int): Boolean = yaml.encodeDefaults

    override fun beginStructure(descriptor: SerialDescriptor): CompositeEncoder {
        val consumer = if (currentTagOrNull == null) {
            valueConsumer
        } else { value -> encodeTaggedConfigValue(currentTag, value) }

        val kind = descriptor.polarisKind
        return when {
            kind.listLike -> YamlConfigListEncoder(yaml, representation, consumer)
            kind.objLike -> YamlConfigEncoder(yaml, representation, consumer)
            kind == StructureKind.MAP -> YamlConfigMapEncoder(yaml, representation, consumer)
            else -> this
        }.also { encoder ->
            if (writeDiscriminator) {
                encoder.encodeTaggedString(ConfigTag(CLASS_DISCRIMINATOR_TAG), descriptor.serialName)
                writeDiscriminator = false
            }
        }
    }

    override fun endEncode(descriptor: SerialDescriptor) {
        valueConsumer(getCurrent())
    }

    private fun nodeValueOf(value: Any?) = representation.represent(value)
}

internal class YamlConfigEncoder(yaml: Yaml, representation: BaseRepresenter, valueConsumer: (Node) -> Unit) :
    AbstractYamlEncoder(yaml, representation, valueConsumer) {

    private val configMap = mutableMapOf<Node, Node>()

    override fun encodeTaggedConfigValue(tag: ConfigTag, value: Node) {
        val key = ScalarNode(Tag.STR, tag.name, ScalarStyle.PLAIN).apply {
            blockComments = tag.comments
        }

        configMap[key] = value
    }

    override fun getCurrent(): Node =
        MappingNode(Tag.MAP, configMap.map { NodeTuple(it.key, it.value) }, FlowStyle.BLOCK)
}

internal class YamlConfigListEncoder(
    yaml: Yaml,
    representation: BaseRepresenter,
    valueConsumer: (Node) -> Unit,
) : AbstractYamlEncoder(yaml, representation, valueConsumer) {

    private val values = mutableListOf<Node>()

    override fun elementName(descriptor: SerialDescriptor, index: Int): String = index.toString()

    override fun encodeTaggedConfigValue(tag: ConfigTag, value: Node) {
        values.add(tag.name.toInt(), value)
    }

    override fun getCurrent(): Node = SequenceNode(Tag.SEQ, values, FlowStyle.BLOCK)
}

internal class YamlConfigMapEncoder(yaml: Yaml, representation: BaseRepresenter, valueConsumer: (Node) -> Unit) :
    AbstractYamlEncoder(yaml, representation, valueConsumer) {

    private val configMap = mutableMapOf<Node, Node>()

    private lateinit var key: Node
    private var isKey: Boolean = true

    override fun encodeTaggedConfigValue(tag: ConfigTag, value: Node) {
        if (isKey) {
            key = if (value.nodeType != NodeType.SCALAR) {
                throw invalidKeyKindException(value.nodeType.name, "Yaml")
            } else {
                value
            }
            isKey = false
        } else {
            configMap[key] = value
            isKey = true
        }
    }

    override fun getCurrent(): Node =
        MappingNode(Tag.MAP, configMap.map { NodeTuple(it.key, it.value) }, FlowStyle.BLOCK)
}

internal data class ConfigTag(val name: String, val comments: List<CommentLine> = emptyList())
