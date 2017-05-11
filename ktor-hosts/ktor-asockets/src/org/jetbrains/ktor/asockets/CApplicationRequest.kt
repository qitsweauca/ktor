package org.jetbrains.ktor.asockets

import kotlinx.http.server.*
import org.jetbrains.ktor.cio.*
import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.request.*
import org.jetbrains.ktor.util.*

class CApplicationRequest(
        override val call: CApplicationCall,
        val request: HttpRequest,
        private val session: Session
) : BaseApplicationRequest() {
    override val cookies by lazy { RequestCookies(this) }

    override val headers: ValuesMap = LazyValuesMap(request)

    override val local = CConnectionPoint(request)

    override val queryParameters by lazy { parseQueryString(request.uri.substringAfter('?', "")) }

    override fun getMultiPartData(): MultiPartData {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    private var readChannel: ReadChannel? = null

    override fun getReadChannel(): ReadChannel {
        if (readChannel != null) return readChannel!!
        readChannel = session.body().forKtor
        return readChannel!!
    }
}