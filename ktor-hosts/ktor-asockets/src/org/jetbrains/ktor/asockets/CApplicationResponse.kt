package org.jetbrains.ktor.asockets

import kotlinx.http.server.*
import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.response.*
import org.jetbrains.ktor.util.*

class CApplicationResponse(override val call: CApplicationCall) : BaseApplicationResponse(call) {
    private val headersMap = ValuesMapBuilder(true)
    private var responseStatus: HttpStatusCode = HttpStatusCode.NotFound

    override val headers = object : ResponseHeaders() {
        override fun getHostHeaderNames(): List<String> = headersMap.names().toList()
        override fun getHostHeaderValues(name: String) = headersMap.getAll(name).orEmpty()

        override fun hostAppendHeader(name: String, value: String) {
            headersMap.append(name, value)
        }
    }

    override fun setStatus(statusCode: HttpStatusCode) {
        responseStatus = statusCode
    }

    internal suspend fun render() {
        val code = responseStatus.value
        val description = statusDescriptionBytes[responseStatus.value] ?: responseStatus.description.toByteArray(Charsets.ISO_8859_1)

        with(call._session) {
            status(code, description, call._request.version)
            for ((name, values) in headersMap.entries()) {
                for (value in values) {
                    header(name, value)
                }
            }

            commit()
        }
    }

    companion object {
        val statusDescriptionBytes = HttpStatusCode.allStatusCodes.associateBy({ it.value }, { it.description.toByteArray(Charsets.ISO_8859_1) })
    }
}
