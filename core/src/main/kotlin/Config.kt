package dev.triumphteam.polaris

import kotlinx.serialization.Serializable
import java.nio.file.Path
import kotlin.reflect.KClass
import kotlin.reflect.KProperty

public interface Config<T> {

    public fun save()

    public fun reload()

    public fun get(): T

    public operator fun getValue(thisRef: Any?, property: KProperty<*>): T

    public operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T)
}

public interface ConfigFormat

public class ConfigBuilder<T : Any>(private val type: KClass<out T>) {

    public lateinit var file: Path

    public lateinit var format: ConfigFormat

    public fun writeDefault(block: () -> T) {

    }

    public fun onFailure(action: (Throwable) -> String) {

    }
}

public inline fun <reified T : Any> loadConfig(block: ConfigBuilder<T>.() -> Unit): Config<T> {

    return TODO("Not yet implemented")
}

@Suppress("UNREACHABLE_CODE")

public fun main() {

    val config = loadConfig<MyConfig> {

        file = Path.of("my-config.conf")

        // Tells the config which format to use, hocon, yaml, etc
        format = TODO("Not yet implemented")

        // Only writes if the file doesn't exist.
        // Maybe in the future some sort of migration to update the file.
        writeDefault { MyConfig("Bob") }

        onFailure {
            "This is to customize your error messages, not sure if it'll stay :')"
        }
    }

    // Get the actual serializable class from the config object.
    val myConfig by config

    println(myConfig.name)

    // Extra functionality.
    config.reload()
    config.save()
}

@Serializable
public data class MyConfig(public val name: String)
