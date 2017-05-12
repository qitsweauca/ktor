package org.jetbrains.ktor.asockets.test

import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.channels.*
import kotlinx.sockets.adapters.*
import kotlinx.sockets.channels.*
import org.jetbrains.ktor.asockets.*
import org.junit.*
import java.io.*
import java.nio.*
import kotlin.test.*

class MultipartParserTest {
    private val pool = Channel<ByteBuffer>(100)

    @Before
    fun setUp() {
        for (i in 1..100) {
            pool.offer(ByteBuffer.allocate(DEFAULT_BUFFER_SIZE))
        }
    }

    @Test
    fun testParser() {
        val out = StringWriter()
        out.apply {
            append("--***bbb***\r\n")
            append("Content-Disposition: form-data; name=\"a story\"\r\n")
            append("\r\n")
            append("Hi user. The snake you gave me for free ate all the birds. Please take it back ASAP.\r\n")
            append("--***bbb***\r\n")
            append("Content-Disposition: form-data; name=\"attachment\"; filename=\"original.txt\"\r\n")
            append("Content-Type: text/plain\r\n")
            append("\r\n")
            append("File content goes here\r\n")
            append("--***bbb***--\r\n")
            flush()
        }

        runBlocking {
            val ch = Channel<ByteBuffer>(1)
            ch.send(ByteBuffer.wrap(out.toString().toByteArray(Charsets.ISO_8859_1)))

            val parser = MultipartParser("***bbb***", ch.bufferedRead(pool))

            val part = parser.next()

            assertEquals("form-data", part?.contentDisposition?.disposition)
            assertEquals("a story", part?.name)
        }
    }

    @Test
    fun testContentBoundaryImmediately() {
        runBlocking {
            val ch = channelOf("\r\n--boundary\r\n")
            val parser = MultipartParser("boundary", ch)

            val bb = ByteBuffer.allocate(DEFAULT_BUFFER_SIZE)
            assertNotEquals(-1, parser.content(bb))
            bb.flip()
            assertEquals("", String(bb.array(), 0, bb.limit()))

            bb.clear()

            assertEquals(-1, parser.content(bb))
        }
    }

    @Test
    fun testContentBoundaryExtraSpaceAfterBoundary() {
        runBlocking {
            val ch = channelOf("\r\n--boundary \r\n")
            val parser = MultipartParser("boundary", ch)

            val bb = ByteBuffer.allocate(DEFAULT_BUFFER_SIZE)
            assertNotEquals(-1, parser.content(bb))
            bb.flip()
            assertEquals("", String(bb.array(), 0, bb.limit()))

            bb.clear()

            assertEquals(-1, parser.content(bb))
        }
    }

    @Test
    fun testContentUnfortunateSplit() {
        runBlocking {
            val text = "123\r\n--boundary \r\n"
            for (splitIndex in 0..text.length) {
                val ch = channelOf(text.substring(0, splitIndex), text.substring(splitIndex))
                val parser = MultipartParser("boundary", ch)

                val bb = ByteBuffer.allocate(DEFAULT_BUFFER_SIZE)
                assertNotEquals(-1, parser.content(bb))
                bb.flip()
                assertEquals("123", String(bb.array(), 0, bb.limit()))

                bb.clear()

                assertEquals(-1, parser.content(bb))
            }
        }
    }

    @Test
    fun testContent() {
        runBlocking {
            val ch = channelOf("abc123\r\nzzz\r\n--boundary\r\n")
            val parser = MultipartParser("boundary", ch)

            val bb = ByteBuffer.allocate(DEFAULT_BUFFER_SIZE)
            assertNotEquals(-1, parser.content(bb))
            bb.flip()
            assertEquals("abc123\r\nzzz", String(bb.array(), 0, bb.limit()))

            bb.clear()

            assertEquals(-1, parser.content(bb))
        }
    }

    private suspend fun channelOf(vararg parts: String): BufferedReadChannel {
        val ch = Channel<ByteBuffer>(parts.size)

        parts.map { ByteBuffer.wrap(it.toByteArray(Charsets.ISO_8859_1)) }.forEach {
            ch.send(it)
        }

        ch.close()

        return ch.bufferedRead(pool)
    }
}