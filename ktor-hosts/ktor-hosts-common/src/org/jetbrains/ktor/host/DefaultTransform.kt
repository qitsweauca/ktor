package org.jetbrains.ktor.host

import org.jetbrains.ktor.cio.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.request.*
import org.jetbrains.ktor.response.*
import org.jetbrains.ktor.util.*
import java.io.*

fun ApplicationSendPipeline.installDefaultTransformations() {
    intercept(ApplicationSendPipeline.Render) { value ->
        val transformed = when (value) {
            is String -> {
                val responseContentType = call.response.headers[HttpHeaders.ContentType]?.let { ContentType.parse(it) }
                val contentType = responseContentType ?: ContentType.Text.Plain.withCharset(Charsets.UTF_8)
                TextContent(value, contentType, null)
            }
            is ByteArray -> {
                ByteArrayContent(value)
            }
            is HttpStatusContent -> {
                TextContent("<H1>${value.code}</H1><P>${value.message.escapeHTML()}</P>",
                        ContentType.Text.Html.withCharset(Charsets.UTF_8),
                        value.code)
            }
            is HttpStatusCode -> {
                HttpStatusCodeContent(value)
            }
            is URIFileContent -> {
                if (value.uri.scheme == "file")
                    LocalFileContent(File(value.uri))
                else
                    null
            }
            else -> null
        }
        if (transformed != null)
            proceedWith(transformed)
    }
}

fun ApplicationReceivePipeline.installDefaultTransformations() {
    intercept(ApplicationReceivePipeline.Transform) { query ->
        val value = query.value as? IncomingContent ?: return@intercept
        val transformed: Any? = when (query.type) {
            ReadChannel::class -> value.readChannel()
            InputStream::class -> value.inputStream()
            MultiPartData::class -> value.multiPartData()
            String::class -> value.readText()
            ValuesMap::class -> {
                val contentType = value.request.contentType()
                when {
                    contentType.match(ContentType.Application.FormUrlEncoded) -> {
                        val string = value.readText()
                        parseQueryString(string)
                    }
                    contentType.match(ContentType.MultiPart.FormData) -> {
                        val items = value.multiPartData().parts.filterIsInstance<PartData.FormItem>()
                        ValuesMap.build {
                            items.forEach {
                                it.partName?.let { name -> append(name, it.value) }
                            }
                        }
                    }
                    else -> null // Respond UnsupportedMediaType? but what if someone else later would like to do it?
                }
            }
            else -> null
        }
        if (transformed != null)
            proceedWith(ApplicationReceiveRequest(query.type, transformed))
    }
}

