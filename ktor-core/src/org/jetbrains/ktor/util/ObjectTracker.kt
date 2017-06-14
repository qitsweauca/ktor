package org.jetbrains.ktor.util

import org.jetbrains.ktor.logging.*
import java.lang.ref.*
import java.util.concurrent.locks.*
import kotlin.concurrent.*

class ObjectTracker<T : Any>(val id: (T) -> String = { System.identityHashCode(it).toString(16) }, val logger: ApplicationLog) {
    private val l = ReentrantLock()

    private val collected = ReferenceQueue<T>()
    private val tracks = HashMap<Reference<T>, Tracking>(8192)

    private val releaseUpdater = newUpdater(ObjectTracker<T>.Tracking::releaseStackTrace)

    fun enter(instance: T): Ticket<T> = l.withLock {
        val t = Tracking(instance, collected)
        tracks[t.reference] = t
        t
    }

    fun leave(ticket: Ticket<T>): Unit = l.withLock {
        tracks.remove(ticket.reference)?.released()
    }

    fun assertEverythingCollected() = l.withLock {
        val failures = processCollected()

        for (t in tracks.values) {
            t.assertCollected()
        }

        tracks.clear()

        if (failures) throw IllegalStateException("There were buffers unreleased")
    }

    private fun processCollected(): Boolean {
        System.gc()
        System.gc()
        System.gc()

        Thread.sleep(30)

        var failures = false

        for (i in 1..3) {
            Thread.yield()
            System.gc()

            do {
                val b = collected.poll() ?: break
                tracks.remove(b)?.collected()?.let { failures = failures || it }
            } while (true)
        }

        return failures
    }

    interface Ticket<T : Any> {
        val reference: WeakReference<T>
    }

    private inner class Tracking(instance: T, q: ReferenceQueue<in T>): Ticket<T> {
        override val reference = WeakReference<T>(instance, q)
        private val hash = id(instance)

        private val allocationStackTrace = Exception().apply {
            fillInStackTrace()
        }

        @Volatile
        @JvmField
        var releaseStackTrace: Exception? = null

        @Volatile
        private var collected = false

        fun collected(): Boolean {
            collected = true

            if (releaseStackTrace == null) {
                // collected but not released
                // TODO
                logger.error(buildString(8192) {
                    appendln("Buffer $hash hasn't been released, most likely there is a leak")
                    appendInfo()
                })
                return true
            }

            return false
        }

        fun released() {
            val e = Exception()
            e.fillInStackTrace()

            if (!releaseUpdater.compareAndSet(this, null, e)) {
                doubleReleaseError()
            }
        }

        fun assertCollected() {
            // TODO
            if (!collected) {
                logger.error(buildString(8192) {
                    appendln("Buffer $hash hasn't been collected, most likely there is a leak")
                    appendInfo()
                })
                throw IllegalStateException("Buffer hasn't been collected")
            }
        }

        private fun doubleReleaseError(): Nothing {
            logger.error(buildString(8192) {
                appendln("Buffer $hash has been released twice")
                appendInfo()
                appendln("  Released again at:")
                appendStackTrace(Exception().stackTrace)
            })

            throw IllegalStateException("Couldn't release: already released")
        }

        private fun StringBuilder.appendInfo() {
            appendln("    - released? ${if (releaseStackTrace == null) "no" else "yes"}")
            appendln("    - collected? ${if (collected) "yes" else "no"}")
            appendln("  Allocated at:")
            appendStackTrace(allocationStackTrace.stackTrace)
            releaseStackTrace?.let {
                appendln("  Released at:")
                appendStackTrace(it.stackTrace)
            }
        }

        private fun StringBuilder.appendStackTrace(stackTrace: Array<StackTraceElement>) {
            val excludedName = ObjectTracker::class.java.simpleName

            stackTrace.filter { !it.className.contains(excludedName) }.forEach { e ->
                append("    at ${e.className}.${e.methodName}")
                if (e.fileName != null) {
                    append("(")
                    append(e.fileName)
                    if (e.lineNumber != -1) {
                        append(":")
                        append(e.lineNumber)
                    }
                    append(")")
                }
                appendln()
            }
        }

    }
}

