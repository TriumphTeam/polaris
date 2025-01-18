package dev.triumphteam.polaris

import kotlin.reflect.KProperty

public class SimpleConfig<T> internal constructor() : Config<T> {

    override fun save() {
        TODO("Not yet implemented")
    }

    override fun reload() {
        TODO("Not yet implemented")
    }

    override fun get(): T {
        TODO("Not yet implemented")
    }

    override fun getValue(thisRef: Any?, property: KProperty<*>): T {
        TODO("Not yet implemented")
    }

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        TODO("Not yet implemented")
    }
}