package org.jetbrains.ktor.asockets

import kotlinx.coroutines.experimental.*
import kotlinx.sockets.*
import org.jetbrains.ktor.cio.*
import java.nio.*
import java.nio.channels.*

class ChunkedResponseWriteChannel(private val buffer: ByteBuffer, val socket: ReadWriteSocket) : WriteChannel {
    private var closed = false

    override fun close() {
        if (!closed) {
            closed = true
            runBlocking {
                buffer.clear()
                buffer.put(0x30)
                buffer.put(0x0d)
                buffer.put(0x0a)
                buffer.put(0x0d)
                buffer.put(0x0a)
                buffer.flip()

                while (buffer.hasRemaining()) {
                    socket.write(buffer)
                }
            }
        }
    }

    suspend override fun flush() {
    }

    suspend override fun write(src: ByteBuffer) {
        if (closed) throw ClosedChannelException()

        if (src.hasRemaining()) {
            buffer.clear().position(10)

            var idx = 7
            var rem = src.remaining()

            while (rem > 0) {
                val v = rem % 16
                rem /= 16

                if (v < 10) {
                    buffer.put(idx, (v + 0x30).toByte())
                } else {
                    buffer.put(idx, (v + 0x61 - 10).toByte())
                }

                idx--
            }

            if (idx == 7) {
                buffer.put(idx, 0)
            } else idx ++

            buffer.put(8, 0x0d)
            buffer.put(9, 0x0a)

            if (src.remaining() <= buffer.capacity() - 12) {
                buffer.put(src)
                buffer.put(0x0d)
                buffer.put(0x0a)
                buffer.flip().position(idx)
                return socket.write(buffer)
            }

            buffer.flip().position(idx)

            while (buffer.hasRemaining()) {
                socket.write(buffer)
            }

            while (src.hasRemaining()) {
                socket.write(src)
            }

            buffer.clear()
            buffer.put(0x0d)
            buffer.put(0x0a)
            buffer.flip()

            while (buffer.hasRemaining()) {
                socket.write(buffer)
            }
        }
    }
}