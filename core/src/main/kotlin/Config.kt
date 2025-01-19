package dev.triumphteam.polaris

import kotlinx.serialization.KSerializer
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer
import java.nio.file.Path
import kotlin.reflect.KProperty

public interface Config<T> {

    public fun save()

    public fun reload()

    public fun get(): T

    public operator fun getValue(thisRef: Any?, property: KProperty<*>): T

    public operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T)
}

public class ConfigBuilder<T : Any> {

    public lateinit var file: Path
    public lateinit var format: PolarisFormat
    public lateinit var defaults: () -> T

    public fun writeDefault(block: () -> T) {
        this.defaults = block
    }

    @PublishedApi
    internal fun build(serializer: (SerializersModule) -> KSerializer<T>): Config<T> {
        return SimpleConfig(file, serializer(format.serializersModule), format, defaults)
    }
}

public inline fun <reified T : Any> loadConfig(block: ConfigBuilder<T>.() -> Unit): Config<T> {
    return ConfigBuilder<T>().apply(block).build { it.serializer<T>() }
}
