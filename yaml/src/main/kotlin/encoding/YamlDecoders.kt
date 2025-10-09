package dev.triumphteam.polaris.yaml.encoding

import dev.triumphteam.polaris.yaml.Yaml
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.AbstractDecoder
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.modules.SerializersModule
import org.snakeyaml.engine.v2.nodes.*

internal class YamlDecoders(
    private val yaml: Yaml,
    private val node: Node
) : AbstractDecoder() {

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
            else -> throw IllegalStateException("Expected scalar node, got ${node.nodeType}")
        }
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

internal class YamlMapDecoder(
    private val yaml: Yaml,
    private val node: MappingNode
) : AbstractDecoder() {

    private var currentIndex = 0
    private val keys = node.value.map { (it.keyNode as ScalarNode).value }
    private val values = node.value.associate {
        (it.keyNode as ScalarNode).value to it.valueNode
    }

    override val serializersModule: SerializersModule = yaml.serializersModule

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
        if (currentIndex >= descriptor.elementsCount) {
            return CompositeDecoder.DECODE_DONE
        }
        return currentIndex++
    }

    override fun decodeString(): String {
        val key = keys.getOrNull(currentIndex - 1) ?: return ""
        val valueNode = values[key] as? ScalarNode ?: return ""
        return valueNode.value
    }

    override fun <T> decodeSerializableElement(
        descriptor: SerialDescriptor,
        index: Int,
        deserializer: DeserializationStrategy<T>,
        previousValue: T?
    ): T {
        val key = descriptor.getElementName(index)
        val valueNode = values[key] ?: throw IllegalStateException("Missing key: $key")
        return deserializer.deserialize(YamlDecoders(yaml, valueNode))
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
        return deserializer.deserialize(YamlDecoders(yaml, elements[index]))
    }
}
