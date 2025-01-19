package dev.triumphteam.polaris.serialization.encoding

import dev.triumphteam.polaris.annotation.SerialComment
import kotlinx.serialization.SerializationException
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.descriptors.PolymorphicKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.findPolymorphicSerializer
import kotlinx.serialization.internal.AbstractPolymorphicSerializer
import kotlinx.serialization.internal.TaggedEncoder

public abstract class AbstractPolarisEncoder<V, T>(protected val valueConsumer: (V) -> Unit) : TaggedEncoder<T>() {

    protected var writeDiscriminator: Boolean = false

    /** Controls what values are passed to the tag. */
    override fun SerialDescriptor.getTag(index: Int): T {

        // Grab the comments of the descriptor.
        val comments = getElementAnnotations(index).filterIsInstance<SerialComment>()
            .firstOrNull()
            ?.value ?: emptyArray()

        return createTag(elementName(this, index), comments.toList())
    }

    protected abstract fun createTag(name: String, comments: List<String>): T

    protected open fun elementName(descriptor: SerialDescriptor, index: Int): String = descriptor.getElementName(index)

    protected abstract fun encodeTaggedConfigValue(tag: T, value: V)
    protected abstract fun getCurrent(): V

    override fun encodeTaggedEnum(tag: T, enumDescriptor: SerialDescriptor, ordinal: Int) {
        encodeTaggedString(tag, enumDescriptor.getElementName(ordinal))
    }

    override fun <T> encodeSerializableValue(serializer: SerializationStrategy<T>, value: T) {
        when {
            serializer !is AbstractPolymorphicSerializer<*> -> serializer.serialize(this, value)

            else -> {
                @Suppress("UNCHECKED_CAST")
                val casted = serializer as AbstractPolymorphicSerializer<Any>
                val actualSerializer = casted.findPolymorphicSerializer(this, value as Any)
                writeDiscriminator = true

                actualSerializer.serialize(this, value)
            }
        }
    }

    override fun endEncode(descriptor: SerialDescriptor) {
        valueConsumer(getCurrent())
    }
}

public val SerialDescriptor.polarisKind: SerialKind
    get() = if (kind is PolymorphicKind) StructureKind.MAP else kind

public val SerialKind.listLike: Boolean
    get() = this == StructureKind.LIST || this is PolymorphicKind

public val SerialKind.objLike: Boolean
    get() = this == StructureKind.CLASS || this == StructureKind.OBJECT

public fun invalidKeyKindException(type: String, format: String): SerializationException = SerializationException(
    "Value of type '${type}' can't be used in $format as a key in the map. " +
            "It should have either primitive or enum kind."
)
