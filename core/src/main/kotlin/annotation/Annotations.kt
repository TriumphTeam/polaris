package dev.triumphteam.polaris.annotation

import kotlinx.serialization.SerialInfo

@SerialInfo
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
public annotation class SerialComment(val value: Array<String> = [])

@DslMarker
public annotation class PolarisDsl