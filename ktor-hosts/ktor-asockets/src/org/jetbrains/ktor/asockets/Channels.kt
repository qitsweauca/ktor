package org.jetbrains.ktor.asockets

import kotlinx.sockets.channels.*
import java.nio.*

val ReadChannel.forKtor: org.jetbrains.ktor.cio.ReadChannel
    get() = object: org.jetbrains.ktor.cio.ReadChannel {
        suspend override fun read(dst: ByteBuffer): Int {
            return this@forKtor.read(dst)
        }

        override fun close() {
        }
    }


val WriteChannel.forKtor: org.jetbrains.ktor.cio.WriteChannel
    get() = object: org.jetbrains.ktor.cio.WriteChannel {
        suspend override fun write(src: ByteBuffer) {
            this@forKtor.write(src)
        }

        suspend override fun flush() {
        }

        override fun close() {
            this@forKtor.shutdownOutput()
        }
    }

