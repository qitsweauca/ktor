package org.jetbrains.ktor.response

import org.jetbrains.ktor.pipeline.*

open class ApplicationSendPipeline : Pipeline<Any>(Before, Transform, Render, ContentEncoding, TransferEncoding, After, Host) {
    companion object Phases {
        /**
         * The earliest phase that happens before any other
         */
        val Before = PipelinePhase("Before")

        /**
         * Transformation phase that can proceed with any supported data like String
         */
        val Transform = PipelinePhase("Transform")

        /**
         * Phase to render any current pipeline subject into [FinalContent]
         *
         * Beyond this phase only [FinalContent] should be produced by any interceptor
         */
        val Render = PipelinePhase("Render")

        /**
         * Phase for processing Content-Encoding, like compression and partial content
         */
        val ContentEncoding = PipelinePhase("ContentEncoding")

        /**
         * Phase for handling Transfer-Encoding, like if chunked encoding is being done manually and not by host
         */
        val TransferEncoding = PipelinePhase("TransferEncoding")

        /**
         * The latest application phase that happens right before host will send the response
         */
        val After = PipelinePhase("After")

        /**
         * Phase for Host to send the response out to client.
         *
         * TODO: this phase will be removed from here later
         */
        val Host = PipelinePhase("Host")
    }
}