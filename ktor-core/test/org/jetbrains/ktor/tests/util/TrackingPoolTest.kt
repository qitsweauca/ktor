package org.jetbrains.ktor.tests.util

import org.jetbrains.ktor.cio.*
import org.jetbrains.ktor.logging.*
import org.junit.*
import java.util.concurrent.*
import kotlin.test.*

class TrackingPoolTest {
    private val messages = CopyOnWriteArrayList<String>()
    private val pool = TrackingByteBufferPool(object: ApplicationLog {
        override val name: String = "test"

        override fun fork(name: String): ApplicationLog {
            return this
        }

        override fun error(message: String, exception: Throwable?) {
//            println(message)

            messages.add(message)
            if (exception != null) {
                messages.add(exception.toString())
            }
        }
    })

    @Test
    fun testSuccess() {
        val t = pool.allocate(0)
        pool.release(t)
        pool.assertEverythingCollected()
        assertTrue { messages.isEmpty() }
    }

    @Test
    fun testNotReleased() {
        val ref = pool.allocate(0)

        assertFails {
            pool.assertEverythingCollected()
        }

        assertNotNull(ref)
        assertTrue { messages.isNotEmpty() }
        assertTrue { "collected? no" in messages[0] }
        assertTrue { "released? no" in messages[0] }
    }

    @Test
    fun testNotReleasedCollected() {
        Allocate(pool).run()

        assertFails {
            pool.assertEverythingCollected()
        }

        assertTrue { messages.isNotEmpty() }
        assertTrue { "released? no" in messages[0] }
        assertTrue("collected? yes" in messages[0], message = messages[0])
    }

    @Test
    fun testReleaseTwice() {
        val t = pool.allocate(0)
        pool.release(t)

        assertFails {
            pool.release(t)
        }
    }



    private class Allocate(val pool: ByteBufferPool) : Runnable {
        override fun run() {
            pool.allocate(0)
        }
    }
}