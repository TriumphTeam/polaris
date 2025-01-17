package dev.triumphteam.polaris.yaml

import dev.triumphteam.polaris.yaml.encoding.YamlConfigEncoder
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationException
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.StringFormat
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule
import org.snakeyaml.engine.v2.api.DumpSettings
import org.snakeyaml.engine.v2.api.StreamDataWriter
import org.snakeyaml.engine.v2.common.FlowStyle
import org.snakeyaml.engine.v2.emitter.Emitter
import org.snakeyaml.engine.v2.nodes.MappingNode
import org.snakeyaml.engine.v2.nodes.Node
import org.snakeyaml.engine.v2.representer.StandardRepresenter
import org.snakeyaml.engine.v2.serializer.Serializer
import java.io.StringWriter

/** Creates an instance of [Yaml] configured by the [builderAction] [YamlBuilder]. */
public fun Yaml(builderAction: YamlBuilder.() -> Unit): Yaml {
    return Yaml.Custom(YamlBuilder().apply(builderAction))
}

/**
 * Allows serialization/deserialization from Yaml into Kotlin objects and vice versa.
 * This instance in itself is [YamlSettings] with immutable values.
 */
public sealed class Yaml(settings: YamlSettings) : StringFormat, YamlSettings by settings {

    /** The default instance for the Hocon format. */
    public companion object Default : Yaml(YamlBuilder())

    /** The custom implementation is created from [YamlBuilder]. */
    internal class Custom(settings: YamlSettings) : Yaml(settings)

    override fun <T> encodeToString(serializer: SerializationStrategy<T>, value: T): String {

        val settings = DumpSettings.builder()
            .setDefaultFlowStyle(FlowStyle.BLOCK)
            .setDumpComments(true)
            .setIndent(indentationSize)
            .setIndicatorIndent(if (indentationSize > 0) 2 else 0)
            .build()

        lateinit var node: Node

        // Encode value into [Node].
        YamlConfigEncoder(this, StandardRepresenter(settings)) { node = it }
            .apply { encodeSerializableValue(serializer, value) }

        if (node !is MappingNode) {
            throw SerializationException(
                "Value of type '${node.nodeType}' can't be used at the root of Yaml Config. " +
                        "It should be either object or map."
            )
        }

        return YamlWriter().apply {
            Serializer(settings, Emitter(settings, this)).apply {
                emitStreamStart()
                serializeDocument(node)
                emitStreamEnd()
            }
        }.toString()
    }

    override fun <T> decodeFromString(deserializer: DeserializationStrategy<T>, string: String): T {
        TODO("Not yet implemented")
    }

    private class YamlWriter : StringWriter(), StreamDataWriter {
        override fun flush() {}

        override fun write(str: String) {
            // Hack comment writing to add a space.
            if (str == "#") {
                super.write("$str ")
                return
            }

            super.write(str)
        }
    }
}

/** Base settings that are used by the [Yaml] serializers. */
public interface YamlSettings {

    /** The kotlinx.serialization [SerializersModule] to be used. */
    public val serializersModule: SerializersModule

    /** Whether it should encode defaults.  */
    public val encodeDefaults: Boolean

    /** Whether nulls should be explicitly written. */
    public val explicitNulls: Boolean

    /**
     * How long the indentation should be. */
    public val indentationSize: Int
}

/** Builder of the [Yaml] instance provided by `Yaml` factory function. */
public class YamlBuilder internal constructor() : YamlSettings {

    /**
     * Module with contextual and polymorphic serializers to be used in the resulting [Yaml] instance.
     * [EmptySerializersModule] by default.
     */
    override var serializersModule: SerializersModule = EmptySerializersModule()

    /**
     * Specifies whether default values of Kotlin properties should be encoded.
     * `true` by default.
     */
    override var encodeDefaults: Boolean = true

    /**
     * Specifies whether `null` values should be encoded for nullable properties and must be present in Node
     * during decoding.
     *
     * When this flag is disabled properties with `null` values are not encoded;
     * during decoding, the absence of a field value is treated as `null` for nullable properties without a default value.
     *
     * `false` by default.
     */
    override var explicitNulls: Boolean = false

    /**
     * Specifies how long the indentation should be.
     * `4` by default.
     */
    override var indentationSize: Int = 4
}
