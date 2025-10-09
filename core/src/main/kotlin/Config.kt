package dev.triumphteam.polaris

import kotlinx.serialization.KSerializer
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer
import java.nio.file.Path
import kotlin.reflect.KProperty

public interface Config<T> {

    public fun save(transform: (T) -> T = { it })

    public fun reload()

    public fun get(): T

    public operator fun getValue(thisRef: Any?, property: KProperty<*>): T

    public operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T)
}

public class ConfigBuilder<T : Any> {

    public lateinit var file: Path
    public lateinit var format: PolarisFormat
    private lateinit var defaults: () -> T
    public var writeDefaults: Boolean = true

    public fun defaultInstance(block: () -> T) {
        this.defaults = block
    }

    @PublishedApi
    internal fun build(serializer: (SerializersModule) -> KSerializer<T>): Config<T> {
        require(::file.isInitialized) { "A file path must be provided to the config builder, use `file = Path.of(...)`." }
        require(::format.isInitialized) { "A format must be provided to the config builder, use `format = `." }
        require(::defaults.isInitialized) { "A default instance provider must be provided to the config builder, use `writeDefault {}`." }

        return SimpleConfig(file, serializer(format.serializersModule), format, defaults, writeDefaults)
    }
}

public inline fun <reified T : Any> loadConfig(block: ConfigBuilder<T>.() -> Unit): Config<T> {
    return ConfigBuilder<T>().apply(block).build { it.serializer<T>() }
}

public inline fun <reified T : Any> loadConfigDirect(block: ConfigBuilder<T>.() -> Unit): T {
    return loadConfig<T>(block).get()
}
