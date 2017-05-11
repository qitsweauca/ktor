package org.jetbrains.ktor.asockets

import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.channels.*
import kotlinx.http.server.*
import kotlinx.sockets.selector.*
import org.jetbrains.ktor.host.*
import java.nio.*
import java.util.concurrent.*

class CApplicationHost(environment: ApplicationHostEnvironment) : BaseApplicationHost(environment) {
    private val ioPool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 2)
    private val taskPool = Executors.newFixedThreadPool(200)

    private val ioDispatcher = ioPool.asCoroutineDispatcher()
    private val taskDispatcher = taskPool.asCoroutineDispatcher()

    private val bufferSize = DEFAULT_BUFFER_SIZE
    private val buffersCount = 100000
    private val bufferPool = Channel<ByteBuffer>(buffersCount)

    private var jobs: List<Job> = emptyList()
    private var manager: ExplicitSelectorManager? = null

    private val handler = object : RequestHandler {
        suspend override fun handle(request: HttpRequest, session: Session) {
            launch(taskDispatcher) {
                pipeline.execute(CApplicationCall(environment.application, request, session, bufferPool))
            }.join()
        }
    }

    override fun start(wait: Boolean): ApplicationHost {
        environment.start()

        val selectors = ExplicitSelectorManager()
        manager = selectors

        launch(taskDispatcher) {
            for (i in 1..buffersCount) {
                bufferPool.offer(ByteBuffer.allocate(bufferSize))
            }
        }

        jobs = environment.connectors.map {
            if (it.type != ConnectorType.HTTP) throw IllegalStateException("Only plain HTTP connectors supported")

            runServer(ioDispatcher, it.port, bufferPool, handler)
        }

        while (wait && !ioPool.isTerminated) {
            ioPool.awaitTermination(100L, TimeUnit.DAYS)
        }

        return this
    }

    override fun stop(gracePeriod: Long, timeout: Long, timeUnit: TimeUnit) {
        // TODO gracefull shutdown
        taskPool.shutdown()
        taskPool.awaitTermination(gracePeriod, timeUnit)

        jobs.forEach { it.cancel() }

        ioPool.shutdown()
        ioPool.awaitTermination(gracePeriod, timeUnit)

        manager?.let {
            it.close()
            manager = null
        }

        while (true) {
            bufferPool.poll() ?: break
        }

        environment.stop()
    }

    object Factory : ApplicationHostFactory<CApplicationHost> {
        override fun create(environment: ApplicationHostEnvironment) = CApplicationHost(environment)
    }
}
