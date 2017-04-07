package org.jetbrains.ktor.pipeline

@DslMarker
@Target(*arrayOf(AnnotationTarget.CLASS, AnnotationTarget.TYPEALIAS))
annotation class PipelineDslMarker

@PipelineDslMarker
class PipelineContext<TSubject : Any>(private val interceptors: List<PipelineInterceptor<TSubject>>, subject: TSubject) {
    var subject: TSubject = subject
        internal set

    private var index = 0

    fun finish() {
        index = -1
    }

    suspend fun proceedWith(subject: TSubject): TSubject {
        this.subject = subject
        return proceed()
    }

    suspend fun proceed(): TSubject {
        while (index >= 0) {
            if (interceptors.size == index) {
                index = -1 // finished
                return subject
            }
            val executeInterceptor = interceptors[index]
            index++
            executeInterceptor.invoke(this, subject)
        }
        return subject
    }
}
