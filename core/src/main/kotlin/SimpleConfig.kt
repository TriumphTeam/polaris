package dev.triumphteam.polaris

import kotlinx.serialization.KSerializer
import java.nio.file.Path
import kotlin.io.path.createParentDirectories
import kotlin.io.path.notExists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.reflect.KProperty

internal class SimpleConfig<T> internal constructor(
    private val path: Path,
    private val serializer: KSerializer<T>,
    private val parser: PolarisFormat,
    private val defaults: () -> T,
) : Config<T> {

    private var value = loadConfig()

    override fun save(transform: (T) -> T) {
        writeToPath(transform(value))
    }

    override fun reload() {
        value = loadConfig()
    }

    override fun get(): T {
        return value
    }

    override fun getValue(thisRef: Any?, property: KProperty<*>): T {
        return get()
    }

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        this.value = value
    }

    private fun loadConfig(): T {
        // Write defaults if the file doesn't exist.
        if (path.notExists()) {
            return writeToPath(defaults())
        }

        // If it exists we just read it.
        return parser.decodeFromString(serializer, path.readText())
    }

    private fun writeToPath(value: T): T {
        if (path.notExists()) {
            path.createParentDirectories()
        }

        return value.also { path.writeText(parser.encodeToString(serializer, it)) }
    }
}
