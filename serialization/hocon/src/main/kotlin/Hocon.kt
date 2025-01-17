package dev.triumphteam.polaris.hocon

import com.typesafe.config.Config
import com.typesafe.config.ConfigObject
import com.typesafe.config.ConfigRenderOptions
import com.typesafe.config.ConfigValue
import dev.triumphteam.polaris.hocon.HoconSettings.CommentStyle
import dev.triumphteam.polaris.hocon.encoding.HoconConfigEncoder
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationException
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.StringFormat
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule

/** Creates an instance of [Hocon] configured by the [builderAction] [HoconBuilder]. */
public fun Hocon(builderAction: HoconBuilder.() -> Unit): Hocon {
    return Hocon.Custom(HoconBuilder().apply(builderAction))
}

/**
 * Allows serialization/deserialization from Hocon into Kotlin objects and vice versa.
 * This instance in itself is [HoconSettings] with immutable values.
 */
public sealed class Hocon(settings: HoconSettings) : StringFormat, HoconSettings by settings {

    /** The default instance for the Hocon format. */
    public companion object Default : Hocon(HoconBuilder())

    /** The custom implementation is created from [HoconBuilder]. */
    internal class Custom(settings: HoconSettings) : Hocon(settings)

    /**
     * Serializes and encodes the given [value] to string using the given [serializer].
     *
     * This serialization applies some defaults to the Hocon renderer.
     * - `setJson` is set to false.
     * - `setOriginComments` is set to false.
     * Other values like `setFormatted` and `setCommentPrefix` are controlled by the [HoconSettings].
     *
     * @throws SerializationException in case of any encoding-specific error
     * @throws IllegalArgumentException if the encoded input does not comply format's specification
     */
    override fun <T> encodeToString(serializer: SerializationStrategy<T>, value: T): String {
        return encodeToConfig(serializer, value).root().render(
            ConfigRenderOptions.defaults()
                .setJson(false)
                .setFormatted(prettyPrint)
                .setOriginComments(false)
                .setCommentPrefix(commentStyle.prefix)
        )
    }

    /**
     * Serializes and encodes the given [value] to [Config] using the given [serializer].
     * This serialization returns the raw [Config] and render maybe different from the [HoconSettings].
     *
     * @throws SerializationException in case of any encoding-specific error
     * @throws IllegalArgumentException if the encoded input does not comply format's specification
     */
    public fun <T> encodeToConfig(serializer: SerializationStrategy<T>, value: T): Config {
        lateinit var configValue: ConfigValue

        // Encode value into [ConfigValue].
        HoconConfigEncoder(this) { configValue = it }
            .apply { encodeSerializableValue(serializer, value) }

        if (configValue !is ConfigObject) {
            throw SerializationException(
                "Value of type '${configValue.valueType()}' can't be used at the root of HOCON Config. " +
                        "It should be either object or map."
            )
        }

        return (configValue as ConfigObject).toConfig()
    }

    override fun <T> decodeFromString(deserializer: DeserializationStrategy<T>, string: String): T {
        TODO("Not yet implemented")
    }
}

/** Base settings that are used by the [Hocon] serializers. */
public interface HoconSettings {

    /** The kotlinx.serialization [SerializersModule] to be used. */
    public val serializersModule: SerializersModule

    /** Which [CommentStyle] to use when writing comments. */
    public val commentStyle: CommentStyle

    /** Whether it should encode defaults.  */
    public val encodeDefaults: Boolean

    /** Whether nulls should be explicitly written. */
    public val explicitNulls: Boolean

    /** Whether the final result should be formatted nicely. */
    public val prettyPrint: Boolean

    /** Enum to control how comments should be written in the hocon config. */
    public enum class CommentStyle(public val prefix: String) {
        HASH_TAG("#"), DOUBLE_SLASH("//")
    }
}

/** Builder of the [Hocon] instance provided by `Hocon` factory function. */
public class HoconBuilder internal constructor() : HoconSettings {

    /**
     * Module with contextual and polymorphic serializers to be used in the resulting [Hocon] instance.
     * [EmptySerializersModule] by default.
     */
    override var serializersModule: SerializersModule = EmptySerializersModule()

    /**
     * Which [CommentStyle] to use when writing comments.
     * [CommentStyle.DOUBLE_SLASH] by default.
     */
    override var commentStyle: CommentStyle = CommentStyle.DOUBLE_SLASH

    /**
     * Specifies whether default values of Kotlin properties should be encoded.
     * `true` by default.
     */
    override var encodeDefaults: Boolean = true

    /**
     * Specifies whether `null` values should be encoded for nullable properties and must be present in ConfigValue
     * during decoding.
     *
     * When this flag is disabled properties with `null` values are not encoded;
     * during decoding, the absence of a field value is treated as `null` for nullable properties without a default value.
     *
     * `false` by default.
     */
    override var explicitNulls: Boolean = false

    /**
     * Specifies whether resulting HOCON should be pretty-printed: formatted and optimized for human readability.
     * `false` by default.
     */
    override var prettyPrint: Boolean = true
}
