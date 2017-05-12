package org.jetbrains.ktor.asockets

import kotlinx.sockets.channels.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.util.*
import java.nio.*

class MultipartParser(val boundary: String, val read: BufferedReadChannel) {
    private val boundaryBytes = ("--" + boundary).toByteArray(Charsets.ISO_8859_1)

    sealed class PartHeader(val headers: ValuesMap) {
        object Preamble : PartHeader(ValuesMap.Empty)
        class Form(value: String, headers: ValuesMap) : PartHeader(headers)
        class File(headers: ValuesMap) : PartHeader(headers) {
            val originalFileName = contentDisposition?.parameter(ContentDisposition.Parameters.FileName)
        }
        object Epilogue : PartHeader(ValuesMap.Empty)

        val contentDisposition: ContentDisposition? by lazy {
            headers[HttpHeaders.ContentDisposition]?.let { ContentDisposition.parse(it) }
        }

        val name: String?
            get() = contentDisposition?.name

        val contentType: ContentType? by lazy { headers[HttpHeaders.ContentType]?.let { ContentType.parse(it) } }
    }

    suspend fun next(): PartHeader? {
        val headers = headers()

        return PartHeader.Form("", headers)
    }

    suspend fun content(dst: ByteBuffer): Int {
        var cr = false

        return read.fold(-1) { read, buffer, last, stop ->
            var count = read.coerceAtLeast(0)

            loop@for (idx in buffer.position() .. buffer.limit() - 1) {
                val b = buffer[idx]
                when {
                    b.toChar() == '\r' -> cr = true
                    b.toChar() == '\n' -> {
                        var boundaryFound = true
                        val remaining = buffer.limit() - idx - 1
                        if (remaining < boundaryBytes.size + 2) {
                            if (last) throw ParserException("No trailing boundary found")

                            buffer.position(idx - (if (cr) 1 else 0))
                            break@loop // await for retry
                        }

                        for (i2 in 0..boundaryBytes.size - 1) {
                            if (buffer[idx + i2 + 1] != boundaryBytes[i2]) {
                                boundaryFound = false
                                break
                            }
                        }

                        // looking for crlf
                        var afterBoundary = idx + boundaryBytes.size + 1
                        afterBoundaryLookup@while (boundaryFound && afterBoundary < buffer.limit()) {
                            val peek = buffer[afterBoundary]

                            when {
                                peek == 0x0a.toByte() -> break@afterBoundaryLookup
                                peek.toChar().isWhitespace() -> afterBoundary++
                                else -> boundaryFound = false
                            }
                        }

                        if (boundaryFound && afterBoundary == buffer.limit()) {
                            if (last) throw ParserException("No trailing boundary found")

                            buffer.position(idx - (if (cr) 1 else 0))
                            break@loop // await for retry
                        }

                        if (boundaryFound) {
                            buffer.position(afterBoundary + 1)
                            stop()
                            break@loop
                        } else {
                            if (cr) {
                                if (!dst.hasRemaining()) {
                                    buffer.position(idx - 1) // rewind to cr
                                    stop()
                                    break@loop
                                } else {
                                    dst.put(0x0d)
                                    count ++
                                    buffer.position(idx)
                                }
                                cr = false
                            }

                            if (!dst.hasRemaining()) {
                                stop()
                                break@loop
                            } else {
                                dst.put(b)
                                count++
                                buffer.position(idx + 1)
                            }
                        }
                    }
                    else -> {
                        cr = false
                        if (!dst.hasRemaining()) {
                            stop()
                            break@loop
                        } else {
                            dst.put(b)
                            count++
                            buffer.position(idx + 1)
                            if (!dst.hasRemaining()) {
                                stop()
                                break@loop
                            }
                        }
                    }
                }
            }

            count
        }
    }

    private suspend fun headers(): ValuesMap {
        return ValuesMap.build(true) {
            do {
                val line = read.readASCIILine(64)
                if (line == null || line.isEmpty()) break

                // TODO shouldn't be here
                if (line.startsWith("--") && line.startsWith(boundary, 2)) continue // ignore boundary

                val sepIndex = line.indexOf(':')
                if (sepIndex == -1) throw ParserException("Illegal part headers line: \n$line")
                var contentIndex = sepIndex + 1

                while (contentIndex < line.length && line[contentIndex].isWhitespace())
                    contentIndex++

                append(line.substring(0, sepIndex), line.substring(contentIndex))
            } while (true)
        }
    }

    private suspend fun skipBoundary() {

    }

    class ParserException(override val message: String) : Exception()
}
