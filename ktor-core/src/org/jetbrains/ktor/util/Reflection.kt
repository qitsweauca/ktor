package org.jetbrains.ktor.util

import java.util.concurrent.atomic.*
import kotlin.reflect.*
import kotlin.reflect.jvm.*

fun KFunction<*>.qualifiedName(): String = javaMethod?.declaringClass?.name + name

inline fun <reified Owner : Any, reified T> newUpdater(p: KProperty1<Owner, T>): AtomicReferenceFieldUpdater<Owner, T> {
    return AtomicReferenceFieldUpdater.newUpdater(Owner::class.java, T::class.java, p.name)
}
