package org.jetbrains.ktor.websocket

import kotlinx.coroutines.experimental.*
import org.jetbrains.ktor.cio.*
import org.jetbrains.ktor.util.*
import java.nio.*

enum class FrameType (val controlFrame: Boolean, val opcode: Int) {
    TEXT(false, 1),
    BINARY(false, 2),
    CLOSE(true, 8),
    PING(true, 9),
    PONG(true, 0xa);

    companion object {
        @Deprecated("Use get function instead")
        val byOpcode = values().associateBy { it.opcode }

        private val maxOpcode = values().maxBy { it.opcode }!!.opcode
        private val byOpcodeArray = Array(maxOpcode + 1) { op -> byOpcode[op] }

        operator fun get(opcode: Int): FrameType? = if (opcode in 0..maxOpcode) byOpcodeArray[opcode] else null
    }
}

sealed class Frame(val fin: Boolean, val frameType: FrameType, buffer: ByteBuffer, disposableHandle: DisposableHandle = NonDisposableHandle) {
    private val initialSize = buffer.remaining()

    @Volatile
    private var disposer: DisposableHandle? = disposableHandle

    var buffer: ByteBuffer = buffer
        private set

    class Binary(fin: Boolean, buffer: ByteBuffer, disposableHandle: DisposableHandle = NonDisposableHandle) : Frame(fin, FrameType.BINARY, buffer, disposableHandle)
    class Text(fin: Boolean, buffer: ByteBuffer, disposableHandle: DisposableHandle = NonDisposableHandle) : Frame(fin, FrameType.TEXT, buffer, disposableHandle) {
        constructor(text: String) : this(true, ByteBuffer.wrap(text.toByteArray(Charsets.UTF_8)))
    }

    class Close(buffer: ByteBuffer, disposableHandle: DisposableHandle = NonDisposableHandle) : Frame(true, FrameType.CLOSE, buffer, disposableHandle) {
        constructor(reason: CloseReason) : this(buildByteBuffer() {
            putShort(reason.code)
            putString(reason.message, Charsets.UTF_8)
        })
        constructor() : this(Empty)
    }
    class Ping(buffer: ByteBuffer, disposableHandle: DisposableHandle = NonDisposableHandle) : Frame(true, FrameType.PING, buffer, disposableHandle)
    class Pong(buffer: ByteBuffer, disposableHandle: DisposableHandle) : Frame(true, FrameType.PONG, buffer, disposableHandle) {
        constructor(buffer: ByteBuffer) : this(buffer, NonDisposableHandle)
    }

    override fun toString() = "Frame $frameType (fin=$fin, buffer len = $initialSize)"

    fun copy(pool: ByteBufferPool = NoPool): Frame {
        if (!buffer.hasRemaining()) {
            return byType(fin, frameType, Empty, NonDisposableHandle)
        }

        val t = buffer.copy(pool)
        return byType(fin, frameType, t.buffer, object: DisposableHandle {
            override fun dispose() {
                pool.release(t)
            }
        })
    }

    fun release() {
        disposerUpdater.getAndSet(this, null)?.dispose()
        buffer = Empty
    }

    companion object {
        private val Empty = ByteBuffer.allocate(0)
        private val disposerUpdater = newUpdater(Frame::disposer)

        fun byType(fin: Boolean, frameType: FrameType, buffer: ByteBuffer, handle: DisposableHandle): Frame = when (frameType) {
            FrameType.BINARY -> Binary(fin, buffer, handle)
            FrameType.TEXT -> Text(fin, buffer, handle)
            FrameType.CLOSE -> Close(buffer, handle)
            FrameType.PING -> Ping(buffer, handle)
            FrameType.PONG -> Pong(buffer, handle)
        }
    }
}

fun Frame.Text.readText(): String {
    require(fin) { "Text could be only extracted from non-fragmented frame" }
    return Charsets.UTF_8.decode(buffer.duplicate()).toString()
}

fun Frame.Close.readReason(): CloseReason? {
    if (buffer.remaining() < 2) {
        return null
    }

    buffer.mark()
    val code = buffer.getShort()
    val message = buffer.getString(Charsets.UTF_8)

    buffer.reset()

    return CloseReason(code, message)
}
