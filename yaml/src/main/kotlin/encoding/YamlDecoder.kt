package dev.triumphteam.polaris.yaml.encoding

import dev.triumphteam.polaris.yaml.Yaml
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.AbstractDecoder
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.modules.SerializersModule
import org.snakeyaml.engine.v2.common.ScalarStyle
import org.snakeyaml.engine.v2.nodes.MappingNode
import org.snakeyaml.engine.v2.nodes.Node
import org.snakeyaml.engine.v2.nodes.ScalarNode
import org.snakeyaml.engine.v2.nodes.SequenceNode
import org.snakeyaml.engine.v2.nodes.Tag

internal class YamlDecoder(private val yaml: Yaml, private val node: Node) : AbstractDecoder() {

    override val serializersModule: SerializersModule = yaml.serializersModule

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
        return CompositeDecoder.DECODE_DONE
    }

    override fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder {
        return when (node) {
            is MappingNode -> YamlMapDecoder(yaml, node)
            is SequenceNode -> YamlListDecoder(yaml, node)
            else -> this
        }
    }

    override fun decodeString(): String {
        return when (node) {
            is ScalarNode -> node.value
            else -> throw SerializationException("Expected scalar node, got ${node.nodeType}")
        }
    }

    override fun decodeValue(): Any = when (node) {
        is ScalarNode -> {
            val value = node.value
            when {
                value.equals("true", ignoreCase = true) -> true
                value.equals("false", ignoreCase = true) -> false
                value.toIntOrNull() != null -> value.toInt()
                value.toLongOrNull() != null -> value.toLong()
                value.toDoubleOrNull() != null -> value.toDouble()
                else -> value
            }
        }

        is MappingNode -> {
            val map = mutableMapOf<String, Any>()
            node.value.forEach { tuple ->
                val keyNode = tuple.keyNode
                val valueNode = tuple.valueNode
                val key = (keyNode as? ScalarNode)?.value ?: return@forEach
                map[key] = YamlDecoder(yaml, valueNode).decodeValue()
            }
            map
        }

        is SequenceNode -> node.value.map { YamlDecoder(yaml, it).decodeValue() }

        else -> throw SerializationException("Unsupported node type: ${node.nodeType}")
    }

    override fun decodeBoolean(): Boolean = decodeString().toBoolean()
    override fun decodeByte(): Byte = decodeString().toByte()
    override fun decodeShort(): Short = decodeString().toShort()
    override fun decodeInt(): Int = decodeString().toInt()
    override fun decodeLong(): Long = decodeString().toLong()
    override fun decodeFloat(): Float = decodeString().toFloat()
    override fun decodeDouble(): Double = decodeString().toDouble()
    override fun decodeChar(): Char = decodeString().single()

    override fun decodeNotNullMark(): Boolean {
        return node !is ScalarNode || node.value != "null"
    }

    override fun decodeNull(): Nothing? = null
}

internal class YamlMapDecoder(private val yaml: Yaml, node: MappingNode) : AbstractDecoder() {

    private var currentIndex = 0
    private val entries = node.value.associate {
        (it.keyNode as ScalarNode).value to it.valueNode
    }

    override val serializersModule: SerializersModule = yaml.serializersModule

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
        while (currentIndex < descriptor.elementsCount) {
            val name = descriptor.getElementName(currentIndex)
            if (entries.containsKey(name)) {
                return currentIndex++
            }
            currentIndex++
        }
        return CompositeDecoder.DECODE_DONE
    }

    override fun decodeValue(): Any {
        val key = entries.keys.elementAtOrNull(currentIndex - 1)
            ?: throw SerializationException("Cannot decode untyped value without key")

        val valueNode = entries[key]
            ?: throw SerializationException("No node found for key: $key")

        return YamlDecoder(yaml, valueNode).decodeValue()
    }

    override fun <T> decodeSerializableElement(
        descriptor: SerialDescriptor,
        index: Int,
        deserializer: DeserializationStrategy<T>,
        previousValue: T?
    ): T {
        val name = descriptor.getElementName(index)
        val node = entries[name] ?: return previousValue ?: deserializer.deserialize(
            YamlDecoder(
                yaml,
                ScalarNode(Tag.NULL, "null", ScalarStyle.PLAIN)
            )
        )

        return deserializer.deserialize(YamlDecoder(yaml, node))
    }

    override fun decodeString(): String {
        throw IllegalStateException("decodeString should not be called directly for map structures")
    }
}


internal class YamlListDecoder(
    private val yaml: Yaml,
    node: SequenceNode
) : AbstractDecoder() {

    private var currentIndex = 0
    private val elements = node.value

    override val serializersModule: SerializersModule = yaml.serializersModule

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
        if (currentIndex >= elements.size) return CompositeDecoder.DECODE_DONE
        return currentIndex++
    }

    override fun decodeCollectionSize(descriptor: SerialDescriptor): Int = elements.size

    override fun <T> decodeSerializableElement(
        descriptor: SerialDescriptor,
        index: Int,
        deserializer: DeserializationStrategy<T>,
        previousValue: T?
    ): T {
        return deserializer.deserialize(YamlDecoder(yaml, elements[index]))
    }

    override fun decodeValue(): Any {
        val elementNode = elements.getOrNull(currentIndex - 1)
            ?: throw SerializationException("Cannot decode untyped value at index $currentIndex")
        return YamlDecoder(yaml, elementNode).decodeValue()
    }
}
