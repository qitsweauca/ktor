package org.jetbrains.ktor.websocket

import org.jetbrains.ktor.cio.*
import org.jetbrains.ktor.util.*
import java.io.*
import java.nio.*

internal class SimpleFrameCollector(val pool: ByteBufferPool): Closeable {
    private var remaining: Int = 0
    private var ticket: PoolTicket? = null
    private var buffer: ByteBuffer? = null
    private val maskBuffer = ByteBuffer.allocate(4)

    val hasRemaining: Boolean
        get() = remaining > 0

    fun start(length: Int, bb: ByteBuffer) {
        require(remaining == 0) { throw IllegalStateException("remaining should be 0") }

        remaining = length
        if (buffer == null || buffer!!.capacity() < length) {
            disposeTicket()
            val newTicket = pool.allocate(length)
            ticket = newTicket
            buffer = newTicket.buffer
        }
        
        buffer!!.clear()

        handle(bb)
    }

    fun handle(bb: ByteBuffer) {
        remaining -= bb.putTo(buffer!!, remaining)
    }

    fun take(maskKey: Int?) = buffer!!.run {
        flip()

        val view = slice()

        if (maskKey != null) {
            maskBuffer.clear()
            maskBuffer.asIntBuffer().put(maskKey)
            maskBuffer.clear()

            view.xor(maskBuffer)
        }

        buffer = null
        view.asReadOnlyBuffer()
    }

    override fun close() {
        disposeTicket()
    }

    private fun disposeTicket() {
        ticket?.let {
            pool.release(it)
            buffer = null
            ticket = null
        }
    }
}