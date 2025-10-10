@file:Suppress("UNCHECKED_CAST")

package dev.triumphteam.polaris.yaml.encoding

import dev.triumphteam.polaris.encoding.listLike
import dev.triumphteam.polaris.encoding.objLike
import dev.triumphteam.polaris.encoding.polarisKind
import dev.triumphteam.polaris.yaml.Yaml
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PolymorphicKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.CompositeDecoder.Companion.DECODE_DONE
import kotlinx.serialization.internal.AbstractPolymorphicSerializer
import kotlinx.serialization.internal.TaggedDecoder
import kotlinx.serialization.modules.SerializersModule

internal abstract class AbstractYamlDecoder<T>(protected val yaml: Yaml) : TaggedDecoder<T>() {

    override val serializersModule: SerializersModule
        get() = yaml.serializersModule

    abstract fun <E> getValueFromTaggedData(tag: T, valueResolver: (Map<String, *>, String) -> E): E

    private inline fun <reified E : Any> validateAndCast(tag: T): E {
        return getValueFromTaggedData(tag) { data, path -> data[path] } as? E ?: throw SerializationException(
            "Value `$tag` is required to be of type ${E::class.simpleName}."
        )
    }

    private fun getTaggedNumber(tag: T) = validateAndCast<Number>(tag)

    override fun decodeTaggedString(tag: T) = validateAndCast<String>(tag)

    override fun decodeTaggedBoolean(tag: T) = validateAndCast<Boolean>(tag)
    override fun decodeTaggedByte(tag: T): Byte = getTaggedNumber(tag).toByte()
    override fun decodeTaggedShort(tag: T): Short = getTaggedNumber(tag).toShort()
    override fun decodeTaggedInt(tag: T): Int = getTaggedNumber(tag).toInt()
    override fun decodeTaggedLong(tag: T): Long = getTaggedNumber(tag).toLong()
    override fun decodeTaggedFloat(tag: T): Float = getTaggedNumber(tag).toFloat()
    override fun decodeTaggedDouble(tag: T): Double = getTaggedNumber(tag).toDouble()

    override fun decodeTaggedChar(tag: T): Char {
        return validateAndCast<String>(tag).let {
            if (it.length != 1) throw SerializationException("String \"$it\" is not convertible to Char")
            it[0]
        }
    }

    override fun decodeTaggedValue(tag: T): Any = getValueFromTaggedData(tag) { data, string ->
        data[string] ?: throw SerializationException("Missing tag value for '$string'.")
    }

    override fun decodeTaggedEnum(tag: T, enumDescriptor: SerialDescriptor): Int {
        return enumDescriptor.getElementIndexOrThrow(validateAndCast<String>(tag))
    }
}

internal class YamlDecoder(yaml: Yaml, private val data: Map<String, *>, private val isPolymorphic: Boolean = false) :
    AbstractYamlDecoder<String>(yaml) {
    private var index = -1

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
        while (++index < descriptor.elementsCount) {
            val name = descriptor.getTag(index)
            if (name in data) return index
        }
        return DECODE_DONE
    }

    private fun composeName(parentName: String, childName: String) =
        if (parentName.isEmpty()) childName else "$parentName.$childName"

    override fun SerialDescriptor.getTag(index: Int): String {
        val conventionName = getElementName(index)
        return if (!isPolymorphic) composeName(currentTagOrNull.orEmpty(), conventionName) else conventionName
    }

    override fun decodeNotNullMark(): Boolean {
        // Tag might be null for top-level deserialization
        val currentTag = currentTagOrNull ?: return data.isNotEmpty()
        return decodeTaggedNotNullMark(currentTag)
    }

    override fun <T> decodeSerializableValue(deserializer: DeserializationStrategy<T>): T {
        return when {
            deserializer !is AbstractPolymorphicSerializer<*> -> deserializer.deserialize(this)
            else -> {
                val data = if (currentTagOrNull != null) data.getData(currentTag) else data

                val reader = YamlDecoder(yaml, data)
                val type = reader.decodeTaggedString("type")
                val actualSerializer = deserializer.findPolymorphicSerializerOrNull(reader, type)
                    ?: throw SerializationException("Polymorphic serializer was not found for class discriminator '$type'")

                @Suppress("UNCHECKED_CAST")
                (actualSerializer as DeserializationStrategy<T>).deserialize(reader)
            }
        }
    }

    override fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder {
        val kind = descriptor.polarisKind
        return when {
            kind.listLike -> ListYamlDecoder(yaml, data.getList(currentTag))
            kind.objLike -> if (index > -1) YamlDecoder(yaml, data.getData(currentTag)) else this
            kind == StructureKind.MAP ->
                // if the current tag is null - map in the root of config
                MapYamlDecoder(yaml, if (currentTagOrNull != null) data.getData(currentTag) else data)

            else -> this
        }
    }

    override fun <E> getValueFromTaggedData(tag: String, valueResolver: (Map<String, *>, String) -> E): E {
        return valueResolver(data, tag)
    }
}

internal class PolymorphYamlDecoder(yaml: Yaml, private val data: Map<String, *>) : AbstractYamlDecoder<String>(yaml) {
    private var index = -1

    override fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder =
        if (descriptor.kind.objLike) YamlDecoder(yaml, data, isPolymorphic = true) else this

    override fun SerialDescriptor.getTag(index: Int): String = getElementName(index)

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
        index++
        return if (index >= descriptor.elementsCount) DECODE_DONE else index
    }

    override fun <E> getValueFromTaggedData(tag: String, valueResolver: (Map<String, *>, String) -> E): E {
        return valueResolver(data, tag)
    }
}

internal class ListYamlDecoder(yaml: Yaml, private val list: List<*>) : AbstractYamlDecoder<Int>(yaml) {
    private var index = -1

    override fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder =
        when {
            descriptor.kind is PolymorphicKind ->
                PolymorphYamlDecoder(yaml, list.getData(currentTag))

            descriptor.kind.listLike -> ListYamlDecoder(yaml, list.getList(currentTag))
            descriptor.kind.objLike -> YamlDecoder(yaml, list.getData(currentTag))
            descriptor.kind == StructureKind.MAP -> MapYamlDecoder(yaml, list.getData(currentTag))
            else -> this
        }

    override fun SerialDescriptor.getTag(index: Int) = index

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
        index++
        return if (index > list.size - 1) DECODE_DONE else index
    }

    override fun <E> getValueFromTaggedData(tag: Int, valueResolver: (Map<String, *>, String) -> E): E {
        return list[tag] as E
    }
}

internal class MapYamlDecoder(yaml: Yaml, map: Map<String, *>) : AbstractYamlDecoder<Int>(yaml) {
    private var index = -1
    private val keys: List<String>
    private val values: List<*>

    init {
        val entries = map.entries.toList() // to fix traversal order
        keys = entries.map(Map.Entry<String, *>::key)
        values = entries.map(Map.Entry<String, *>::value)
    }

    private val indexSize = values.size * 2

    override fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder =
        when {
            descriptor.kind is PolymorphicKind -> PolymorphYamlDecoder(yaml, values.getData(currentTag / 2))
            descriptor.kind.listLike -> ListYamlDecoder(yaml, values.getList(currentTag / 2))
            descriptor.kind.objLike -> YamlDecoder(yaml, values.getData(currentTag / 2))
            descriptor.kind == StructureKind.MAP -> MapYamlDecoder(yaml, values.getData(currentTag / 2))
            else -> this
        }

    override fun SerialDescriptor.getTag(index: Int) = index

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
        index++
        return if (index >= indexSize) DECODE_DONE else index
    }

    override fun <E> getValueFromTaggedData(tag: Int, valueResolver: (Map<String, *>, String) -> E): E {
        val idx = tag / 2
        if (tag % 2 == 0) return keys[idx] as E // entry as string
        return values[idx] as E
    }
}

private fun Map<String, *>.getData(key: String): Map<String, *> {
    return this[key] as? Map<String, *> ?: throw SerializationException("Missing tag value for '$key'.")
}

private fun List<*>.getData(index: Int): Map<String, *> {
    return this[index] as? Map<String, *> ?: throw SerializationException("Missing list value for index '$index'.")
}

private fun Map<String, *>.getList(key: String): List<*> {
    return this[key] as? List<*> ?: throw SerializationException("Missing tag value for '$key'.")
}

private fun List<*>.getList(index: Int): List<*> {
    return this[index] as? List<*> ?: throw SerializationException("Missing list value for index '$index'.")
}


private fun SerialDescriptor.getElementIndexOrThrow(name: String): Int {
    val index = getElementIndex(name)
    if (index == CompositeDecoder.UNKNOWN_NAME)
        throw SerializationException("$serialName does not contain element with name '$name'")
    return index
}
