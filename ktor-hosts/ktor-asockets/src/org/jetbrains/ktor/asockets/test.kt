package org.jetbrains.ktor.asockets

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.cio.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.features.*
import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.routing.*

fun main(args: Array<String>) {
    embeddedServer(CApplicationHost.Factory, port = 8080) {
//        install(CallLogging)
        install(DefaultHeaders)

        install(Routing) {
            val result = ByteArrayContent(buildString {
                for (i in 1..100000) {
                    append("zzzz")
                    append("\n")
                }
            }.toByteArray())

            get("/") {
                call.respond(object : FinalContent.ReadChannelContent() {
                    override fun readFrom(): ReadChannel {
                        return result.bytes().toReadChannel()
                    }
                })
            }
            post("/echo") {
                val buffer = ByteBufferWriteChannel()
                call.request.receive<ReadChannel>().copyTo(buffer)
                call.respond(buffer.toByteArray())
            }
        }
    }.start(true)
}