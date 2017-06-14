package org.jetbrains.ktor.cio

import org.jetbrains.ktor.logging.*
import org.jetbrains.ktor.util.*
import java.nio.*

class TrackingByteBufferPool(logger: ApplicationLog) : ByteBufferPool {
    private val buffersTracker = ObjectTracker<ByteBuffer>(logger = logger)

    override fun allocate(size: Int): PoolTicket = TrackingTicket(size).also { t ->
        t.subTicket = buffersTracker.enter(t.buffer)
    }

    override fun release(buffer: PoolTicket) {
        if (buffer !is TrackingTicket) throw IllegalArgumentException("Wrong buffer: $buffer")
        if (buffer.buffer === TrackingTicket.Disposed) throw IllegalStateException("Released twice")

        buffersTracker.leave(buffer.subTicket)
        buffer.buffer = TrackingTicket.Disposed
    }

    fun assertEverythingCollected() {
        buffersTracker.assertEverythingCollected()
    }

    private class TrackingTicket(size: Int) : PoolTicket {
        lateinit var subTicket: ObjectTracker.Ticket<ByteBuffer>

        override var buffer: ByteBuffer = ByteBuffer.allocate(size)
            get() {
                if (field === Disposed) throw IllegalStateException("Accessing disposed buffer")
                return field
            }

        companion object {
            internal val Disposed = ByteBuffer.allocate(0)
        }
    }
}