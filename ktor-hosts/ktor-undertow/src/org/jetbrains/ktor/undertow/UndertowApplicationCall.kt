package org.jetbrains.ktor.undertow

import io.undertow.server.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.cio.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.host.*

class UndertowApplicationCall(override val application: Application, val exchange: HttpServerExchange) : BaseApplicationCall(application) {
    suspend override fun respondUpgrade(upgrade: FinalContent.ProtocolUpgrade) {

    }

    override suspend fun responseChannel(): WriteChannel {
        return UndertowWriteChannel(exchange.responseSender)
    }

    override val request = UndertowApplicationRequest(this)
    override val response = UndertowApplicationResponse(this)
}