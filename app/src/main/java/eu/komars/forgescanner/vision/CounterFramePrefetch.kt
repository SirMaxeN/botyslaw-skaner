package eu.komars.forgescanner.vision

/**
 * A single owned result rendezvous. The producer and consumer may finish in either
 * order, but only one future source image can be retained. Confined to the main thread.
 */
internal class CounterFramePrefetch<T>(
    private val release: (T) -> Unit,
    private val cancelRequest: (((T?, String?) -> Unit)) -> Unit
) : AutoCloseable {
    private var started = false
    private var received = false
    private var consumed = false
    private var closed = false
    private var value: T? = null
    private var error: String? = null
    private var consumer: ((T?, String?) -> Unit)? = null
    val finished: Boolean get() = closed || consumed

    private val result: (T?, String?) -> Unit = { item, issue ->
        if (closed || received) item?.let(release)
        else {
            received = true
            value = item
            error = issue
            deliver()
        }
    }

    fun start(request: (((T?, String?) -> Unit)) -> Unit) {
        if (closed || started) return
        started = true
        request(result)
    }

    fun take(callback: (T?, String?) -> Unit) {
        check(!consumed && consumer == null) { "A prefetched source frame has one consumer." }
        if (closed) return
        consumer = callback
        deliver()
    }

    private fun deliver() {
        val callback = consumer ?: return
        if (!received || closed) return
        consumed = true
        consumer = null
        val item = value; val issue = error
        value = null; error = null
        callback(item, issue)
    }

    override fun close() {
        if (closed || consumed) return
        closed = true
        if (started && !received) cancelRequest(result)
        value?.let(release)
        value = null; error = null; consumer = null
    }
}
