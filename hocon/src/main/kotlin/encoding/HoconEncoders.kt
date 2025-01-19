package dev.triumphteam.polaris.hocon.encoding

import com.typesafe.config.ConfigValue
import com.typesafe.config.ConfigValueFactory
import com.typesafe.config.ConfigValueType
import dev.triumphteam.polaris.hocon.Hocon
import dev.triumphteam.polaris.serialization.encoding.AbstractPolarisEncoder
import dev.triumphteam.polaris.serialization.encoding.invalidKeyKindException
import dev.triumphteam.polaris.serialization.encoding.listLike
import dev.triumphteam.polaris.serialization.encoding.objLike
import dev.triumphteam.polaris.serialization.encoding.polarisKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.encoding.CompositeEncoder
import kotlinx.serialization.internal.NamedValueEncoder
import kotlinx.serialization.internal.TaggedEncoder
import kotlinx.serialization.modules.SerializersModule

private const val CLASS_DISCRIMINATOR_TAG = "type"

/**
 * This is a slightly modified version of kotlinx.serialization-hocon.
 * Main change is changing from [NamedValueEncoder] to [TaggedEncoder],
 * this allows the encoding to be more extendable.
 * For example, the [ConfigTag] can now hold multiple types of values, from comment annotation,
 * to possibly custom annotations in the future.
 */
internal abstract class AbstractHoconEncoder(
    private val hocon: Hocon,
    valueConsumer: (ConfigValue) -> Unit,
) : AbstractPolarisEncoder<ConfigValue, ConfigTag>(valueConsumer) {

    override val serializersModule: SerializersModule
        get() = hocon.serializersModule

    override fun createTag(name: String, comments: List<String>): ConfigTag {
        return ConfigTag(name, comments)
    }

    override fun encodeTaggedValue(tag: ConfigTag, value: Any) =
        encodeTaggedConfigValue(tag, configValueOf(value, tag.comments))

    override fun encodeTaggedNull(tag: ConfigTag) {
        if (!hocon.explicitNulls) return
        encodeTaggedConfigValue(tag, configValueOf(null, tag.comments))
    }

    override fun encodeTaggedChar(tag: ConfigTag, value: Char) = encodeTaggedString(tag, value.toString())

    override fun shouldEncodeElementDefault(descriptor: SerialDescriptor, index: Int): Boolean = hocon.encodeDefaults

    override fun beginStructure(descriptor: SerialDescriptor): CompositeEncoder {
        val consumer = if (currentTagOrNull == null) {
            valueConsumer
        } else { value -> encodeTaggedConfigValue(currentTag, value) }

        val kind = descriptor.polarisKind
        val comments = currentTagOrNull?.comments ?: emptyList()

        return when {
            kind.listLike -> HoconConfigListEncoder(hocon, comments, consumer)
            kind.objLike -> HoconConfigEncoder(hocon, consumer)
            kind == StructureKind.MAP -> HoconConfigMapEncoder(hocon, consumer)
            else -> this
        }.also { encoder ->
            if (writeDiscriminator) {
                encoder.encodeTaggedString(ConfigTag(CLASS_DISCRIMINATOR_TAG), descriptor.serialName)
                writeDiscriminator = false
            }
        }
    }

    private fun configValueOf(value: Any?, comment: List<String>) =
        ConfigValueFactory.fromAnyRef(value, null, comment)
}

internal class HoconConfigEncoder(hocon: Hocon, configConsumer: (ConfigValue) -> Unit) :
    AbstractHoconEncoder(hocon, configConsumer) {

    private val configMap = mutableMapOf<String, ConfigValue>()

    override fun encodeTaggedConfigValue(tag: ConfigTag, value: ConfigValue) {
        configMap[tag.name] = value
    }

    override fun getCurrent(): ConfigValue =
        ConfigValueFactory.fromAnyRef(configMap, null, emptyList())
}

internal class HoconConfigListEncoder(
    hocon: Hocon,
    private val comments: List<String>,
    configConsumer: (ConfigValue) -> Unit,
) : AbstractHoconEncoder(hocon, configConsumer) {

    private val values = mutableListOf<ConfigValue>()

    override fun elementName(descriptor: SerialDescriptor, index: Int): String = index.toString()

    override fun encodeTaggedConfigValue(tag: ConfigTag, value: ConfigValue) {
        values.add(tag.name.toInt(), value)
    }

    override fun getCurrent(): ConfigValue =
        ConfigValueFactory.fromAnyRef(values, null, comments)
}

internal class HoconConfigMapEncoder(hocon: Hocon, configConsumer: (ConfigValue) -> Unit) :
    AbstractHoconEncoder(hocon, configConsumer) {

    private val configMap = mutableMapOf<String, ConfigValue>()

    private lateinit var key: String
    private var isKey: Boolean = true

    override fun encodeTaggedConfigValue(tag: ConfigTag, value: ConfigValue) {
        if (isKey) {
            key = when (value.valueType()) {
                ConfigValueType.OBJECT, ConfigValueType.LIST -> throw invalidKeyKindException(
                    value.valueType().toString(), "Hocon"
                )

                else -> value.unwrappedNullable().toString()
            }
            isKey = false
        } else {
            configMap[key] = value
            isKey = true
        }
    }

    override fun getCurrent(): ConfigValue =
        ConfigValueFactory.fromAnyRef(configMap, null, emptyList())

    // Without cast to `Any?` Kotlin will assume unwrapped value as non-nullable by default
    // and will call `Any.toString()` instead of extension-function `Any?.toString()`.
    // We can't cast value in place using `(value.unwrapped() as Any?).toString()` because of warning "No cast needed".
    private fun ConfigValue.unwrappedNullable(): Any? = unwrapped()
}

internal data class ConfigTag(val name: String, val comments: List<String> = emptyList())
