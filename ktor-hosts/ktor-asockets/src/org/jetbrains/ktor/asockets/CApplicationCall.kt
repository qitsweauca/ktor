package org.jetbrains.ktor.asockets

import kotlinx.coroutines.experimental.channels.Channel
import kotlinx.http.server.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.cio.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.http.*
import java.nio.*

class CApplicationCall(application: Application,
                       val _request: HttpRequest,
                       val _session: Session,
                       private val pool: Channel<ByteBuffer>
                       ) : BaseApplicationCall(application) {
    override val request = CApplicationRequest(this, _request, _session)
    override val response = CApplicationResponse(this)

    suspend override fun respondUpgrade(upgrade: FinalContent.ProtocolUpgrade) {
        TODO("not implemented")
    }

    private var responseChannel: WriteChannel? = null

    suspend override fun respondFinalContent(content: FinalContent) {
        commitHeaders(content)

        val buffer = pool.poll() ?: ByteBuffer.allocateDirect(8192)
        try {
            val responseContentLength = response.headers[HttpHeaders.ContentLength]?.toLong()
            responseChannel = when {
                responseContentLength != null -> _session.directOutput(responseContentLength).forKtor
                else -> _session.chunkedOutput().forKtor
            }

            if (responseContentLength == null && HttpHeaders.TransferEncoding !in response.headers) {
                response.headers.append(HttpHeaders.TransferEncoding, "chunked")
            }

            response.render()

            // TODO need to be reused instead
            return when (content) {
                is FinalContent.ProtocolUpgrade -> respondUpgrade(content)

            // ByteArrayContent is most efficient
                is FinalContent.ByteArrayContent -> respondFromBytes(content.bytes())

            // WriteChannelContent is more efficient than ReadChannelContent
                is FinalContent.WriteChannelContent -> content.writeTo(responseChannel())

            // Pipe is least efficient
                is FinalContent.ReadChannelContent -> respondFromChannel(content.readFrom())

            // Do nothing, but maintain `when` exhaustiveness
                is FinalContent.NoContent -> { /* no-op */
                }
            }
        } finally {
            pool.offer(buffer)
        }
    }

    override fun responseChannel(): WriteChannel {
        return responseChannel ?: throw IllegalStateException("Should be only called from respondFinalContent")
    }
}