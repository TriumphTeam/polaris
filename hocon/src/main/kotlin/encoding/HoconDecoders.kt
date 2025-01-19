package dev.triumphteam.polaris.hocon.encoding

import com.typesafe.config.Config
import com.typesafe.config.ConfigException
import com.typesafe.config.ConfigList
import com.typesafe.config.ConfigObject
import com.typesafe.config.ConfigValue
import com.typesafe.config.ConfigValueFactory
import dev.triumphteam.polaris.hocon.Hocon
import dev.triumphteam.polaris.serialization.encoding.listLike
import dev.triumphteam.polaris.serialization.encoding.objLike
import dev.triumphteam.polaris.serialization.encoding.polarisKind
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

internal abstract class AbstractHoconDecoder<T>(protected val hocon: Hocon) : TaggedDecoder<T>() {

    override val serializersModule: SerializersModule
        get() = hocon.serializersModule

    abstract fun <E> getValueFromTaggedConfig(tag: T, valueResolver: (Config, String) -> E): E

    private inline fun <reified E : Any> validateAndCast(tag: T): E {
        return try {
            when (E::class) {
                Number::class -> getValueFromTaggedConfig(tag) { config, path -> config.getNumber(path) } as E
                Boolean::class -> getValueFromTaggedConfig(tag) { config, path -> config.getBoolean(path) } as E
                String::class -> getValueFromTaggedConfig(tag) { config, path -> config.getString(path) } as E
                else -> getValueFromTaggedConfig(tag) { config, path -> config.getAnyRef(path) } as E
            }
        } catch (e: ConfigException) {
            throw SerializationException(
                "${e.origin().description()} required to be of type ${E::class.simpleName}."
            )
        }
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

    override fun decodeTaggedValue(tag: T): Any = getValueFromTaggedConfig(tag) { config, string ->
        config.getAnyRef(string)
    }

    override fun decodeTaggedNotNullMark(tag: T) = getValueFromTaggedConfig(tag) { config, string ->
        !config.getIsNull(string)
    }

    override fun decodeTaggedEnum(tag: T, enumDescriptor: SerialDescriptor): Int {
        return enumDescriptor.getElementIndexOrThrow(validateAndCast<String>(tag))
    }

    internal fun <E> decodeConfigValue(extractValueAtPath: (Config, String) -> E): E =
        getValueFromTaggedConfig(currentTag, extractValueAtPath)

}

internal class HoconDecoder(hocon: Hocon, private val config: Config, private val isPolymorphic: Boolean = false) :
    AbstractHoconDecoder<String>(hocon) {
    private var index = -1

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
        while (++index < descriptor.elementsCount) {
            val name = descriptor.getTag(index)
            if (config.hasPathOrNull(name)) {
                return index
            }
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
        val currentTag = currentTagOrNull ?: return !config.isEmpty
        return decodeTaggedNotNullMark(currentTag)
    }

    override fun <T> decodeSerializableValue(deserializer: DeserializationStrategy<T>): T {
        return when {
            deserializer !is AbstractPolymorphicSerializer<*> -> deserializer.deserialize(this)
            else -> {
                val config = if (currentTagOrNull != null) config.getConfig(currentTag) else config

                val reader = HoconDecoder(hocon, config)
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
            kind.listLike -> ListHoconDecoder(hocon, config.getList(currentTag))
            kind.objLike -> if (index > -1) HoconDecoder(hocon, config.getConfig(currentTag)) else this
            kind == StructureKind.MAP ->
                // if the current tag is null - map in the root of config
                MapHoconDecoder(hocon, if (currentTagOrNull != null) config.getObject(currentTag) else config.root())

            else -> this
        }
    }

    override fun <E> getValueFromTaggedConfig(tag: String, valueResolver: (Config, String) -> E): E {
        return valueResolver(config, tag)
    }
}

internal class PolymorphHoconDecoder(hocon: Hocon, private val conf: Config) : AbstractHoconDecoder<String>(hocon) {
    private var index = -1

    override fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder =
        if (descriptor.kind.objLike) HoconDecoder(hocon, conf, isPolymorphic = true) else this

    override fun SerialDescriptor.getTag(index: Int): String = getElementName(index)

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
        index++
        return if (index >= descriptor.elementsCount) DECODE_DONE else index
    }

    override fun <E> getValueFromTaggedConfig(tag: String, valueResolver: (Config, String) -> E): E {
        return valueResolver(conf, tag)
    }
}

internal class ListHoconDecoder(hocon: Hocon, private val list: ConfigList) : AbstractHoconDecoder<Int>(hocon) {
    private var index = -1

    override fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder =
        when {
            descriptor.kind is PolymorphicKind ->
                PolymorphHoconDecoder(hocon, (list[currentTag] as ConfigObject).toConfig())

            descriptor.kind.listLike -> ListHoconDecoder(hocon, list[currentTag] as ConfigList)
            descriptor.kind.objLike -> HoconDecoder(hocon, (list[currentTag] as ConfigObject).toConfig())
            descriptor.kind == StructureKind.MAP -> MapHoconDecoder(hocon, list[currentTag] as ConfigObject)
            else -> this
        }

    override fun SerialDescriptor.getTag(index: Int) = index

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
        index++
        return if (index > list.size - 1) DECODE_DONE else index
    }

    override fun <E> getValueFromTaggedConfig(tag: Int, valueResolver: (Config, String) -> E): E {
        val tagString = tag.toString()
        val configValue = valueResolver(list[tag].atKey(tagString), tagString)
        return configValue
    }
}

internal class MapHoconDecoder(hocon: Hocon, map: ConfigObject) : AbstractHoconDecoder<Int>(hocon) {
    private var index = -1
    private val keys: List<String>
    private val values: List<ConfigValue>

    init {
        val entries = map.entries.toList() // to fix traversal order
        keys = entries.map(MutableMap.MutableEntry<String, ConfigValue>::key)
        values = entries.map(MutableMap.MutableEntry<String, ConfigValue>::value)
    }

    private val indexSize = values.size * 2

    override fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder =
        when {
            descriptor.kind is PolymorphicKind -> PolymorphHoconDecoder(
                hocon,
                (values[currentTag / 2] as ConfigObject).toConfig()
            )

            descriptor.kind.listLike -> ListHoconDecoder(hocon, values[currentTag / 2] as ConfigList)
            descriptor.kind.objLike -> HoconDecoder(hocon, (values[currentTag / 2] as ConfigObject).toConfig())
            descriptor.kind == StructureKind.MAP -> MapHoconDecoder(hocon, values[currentTag / 2] as ConfigObject)
            else -> this
        }

    override fun SerialDescriptor.getTag(index: Int) = index

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
        index++
        return if (index >= indexSize) DECODE_DONE else index
    }

    override fun <E> getValueFromTaggedConfig(tag: Int, valueResolver: (Config, String) -> E): E {
        val idx = tag / 2
        val tagString = tag.toString()
        val configValue = if (tag % 2 == 0) { // entry as string
            ConfigValueFactory.fromAnyRef(keys[idx]).atKey(tagString)
        } else {
            val configValue = values[idx]
            configValue.atKey(tagString)
        }
        return valueResolver(configValue, tagString)
    }
}

private fun SerialDescriptor.getElementIndexOrThrow(name: String): Int {
    val index = getElementIndex(name)
    if (index == CompositeDecoder.UNKNOWN_NAME)
        throw SerializationException("$serialName does not contain element with name '$name'")
    return index
}