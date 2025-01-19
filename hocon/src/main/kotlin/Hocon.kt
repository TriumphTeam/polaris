package dev.triumphteam.polaris.hocon

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigObject
import com.typesafe.config.ConfigRenderOptions
import com.typesafe.config.ConfigValue
import dev.triumphteam.polaris.PolarisFormat
import dev.triumphteam.polaris.hocon.HoconSettings.CommentStyle
import dev.triumphteam.polaris.hocon.encoding.HoconConfigEncoder
import dev.triumphteam.polaris.hocon.encoding.HoconDecoder
import dev.triumphteam.polaris.loadConfig
import kotlinx.coroutines.delay
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule
import kotlin.io.path.Path
import kotlin.time.Duration.Companion.seconds

/** Creates an instance of [Hocon] configured by the [builderAction] [HoconBuilder]. */
public fun Hocon(builderAction: HoconBuilder.() -> Unit): Hocon {
    return Hocon.Custom(HoconBuilder().apply(builderAction))
}

/**
 * Allows serialization/deserialization from Hocon into Kotlin objects and vice versa.
 * This instance in itself is [HoconSettings] with immutable values.
 */
public sealed class Hocon(settings: HoconSettings) : PolarisFormat, HoconSettings by settings {

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

        return (configValue as ConfigObject).toConfig().root().render(
            ConfigRenderOptions.defaults()
                .setJson(false)
                .setFormatted(prettyPrint)
                .setOriginComments(false)
                .setCommentPrefix(commentStyle.prefix)
        )
    }

    override fun <T> decodeFromString(deserializer: DeserializationStrategy<T>, string: String): T {
        return HoconDecoder(this, ConfigFactory.parseString(string)).decodeSerializableValue(deserializer)
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

public suspend fun main() {

    val config = loadConfig<MyConfig> {

        file = Path("hocon-config.conf")

        // Tells the config which format to use, hocon, yaml, etc
        format = Hocon {
            encodeDefaults = true
            commentStyle = CommentStyle.DOUBLE_SLASH
        }

        // Only writes if the file doesn't exist.
        // Maybe in the future some sort of migration to update the file.
        writeDefault { MyConfig("Bob") }
    }

    // Delegate value to a variable.
    val myConfig by config

    // Print the [MyConfig].
    println(myConfig)

    // Delay for 5 seconds to give me time to edit.
    delay(5.seconds)

    // Reload config object.
    config.reload()

    // Print [MyConfig] again, notice we did not delegate it again.
    println(myConfig)
}

@Serializable
public data class MyConfig(public val name: String)