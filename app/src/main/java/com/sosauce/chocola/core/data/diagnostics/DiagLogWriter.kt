package com.sosauce.chocola.core.data.diagnostics

import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock

/**
 * Single-thread async writer so playback and UI threads do not do synchronous disk I/O.
 * [flushSync] is for crash paths only: it drains the queue and fsyncs.
 */
class DiagLogWriter(
    private val sink: DiagLogFileSink,
    private val queueCapacity: Int = DEFAULT_QUEUE_CAPACITY
) : AutoCloseable {

    private val sinkLock = ReentrantLock()
    private val queue = ArrayDeque<String>(queueCapacity)
    private val dropped = AtomicInteger(0)
    private val running = AtomicBoolean(true)
    private val closed = AtomicBoolean(false)
    private val hasWork = Object()

    private val thread = Thread(
        {
            while (running.get() || peekQueued()) {
                awaitWork()
                // Lines leave the queue only while the sink lock is held, so flushSync() either
                // drains them itself or waits until this batch is on disk. Taking them out
                // before locking let a crash-path flush return while the batch was in flight.
                sinkLock.lock()
                try {
                    val batch = drainBatch()
                    if (batch.isNotEmpty()) {
                        for (line in batch) sink.appendLine(line)
                        maybeNoteDrops()
                        sink.flush(syncFileSystem = true)
                    }
                } finally {
                    sinkLock.unlock()
                }
            }
        },
        "bymusic-diag-log"
    ).apply {
        isDaemon = true
        priority = Thread.NORM_PRIORITY - 1
        start()
    }

    fun enqueue(line: String) {
        var droppedNow = false
        synchronized(queue) {
            if (queue.size >= queueCapacity) {
                queue.pollFirst()
                droppedNow = true
            }
            queue.addLast(line)
        }
        if (droppedNow) dropped.incrementAndGet()
        synchronized(hasWork) { hasWork.notifyAll() }
    }

    fun flushSync() {
        flushInternal(timeoutMs = null, syncFileSystem = true)
    }

    fun flushBestEffort(timeoutMs: Long) {
        flushInternal(timeoutMs = timeoutMs, syncFileSystem = false)
    }

    private fun flushInternal(timeoutMs: Long?, syncFileSystem: Boolean) {
        val locked = if (timeoutMs == null) {
            sinkLock.lock()
            true
        } else {
            sinkLock.tryLock(timeoutMs, TimeUnit.MILLISECONDS)
        }
        // Not locked: the writer thread still owns the queued lines and will write them.
        if (!locked) return
        try {
            val snapshot = ArrayList<String>()
            synchronized(queue) {
                while (queue.isNotEmpty()) snapshot.add(queue.removeFirst())
            }
            for (line in snapshot) sink.appendLine(line)
            maybeNoteDrops()
            sink.flush(syncFileSystem = syncFileSystem)
        } finally {
            sinkLock.unlock()
        }
    }

    fun droppedCount(): Int = dropped.get()

    fun discardPendingAndClear(sinkOverride: DiagLogFileSink? = null) {
        synchronized(queue) { queue.clear() }
        sinkLock.lock()
        try {
            (sinkOverride ?: sink).clear()
        } finally {
            sinkLock.unlock()
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        running.set(false)
        synchronized(hasWork) { hasWork.notifyAll() }
        thread.join(1_000)
        flushSync()
        sinkLock.lock()
        try {
            sink.close()
        } finally {
            sinkLock.unlock()
        }
    }

    private fun peekQueued(): Boolean = synchronized(queue) { queue.isNotEmpty() }

    /** Waits up to 250 ms for queued lines; returns at once when there are some or on close. */
    private fun awaitWork() {
        if (peekQueued() || !running.get()) return
        synchronized(hasWork) {
            if (!peekQueued() && running.get()) {
                try {
                    hasWork.wait(250)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
            }
        }
    }

    /** Call with [sinkLock] held. */
    private fun drainBatch(): List<String> = synchronized(queue) {
        if (queue.isEmpty()) return emptyList()
        val batch = ArrayList<String>(queue.size.coerceAtMost(64))
        while (queue.isNotEmpty() && batch.size < 64) {
            batch.add(queue.removeFirst())
        }
        batch
    }

    private fun maybeNoteDrops() {
        val count = dropped.getAndSet(0)
        if (count > 0) {
            sink.appendLine(
                DiagLogFormatter.format(
                    DiagLogEvent(
                        timestampMillis = System.currentTimeMillis(),
                        zone = java.time.ZoneId.systemDefault(),
                        level = "WARN",
                        tag = "DiagLog",
                        pid = 0,
                        tid = 0,
                        threadName = "bymusic-diag-log",
                        message = "dropped $count log lines because the queue was full"
                    )
                )
            )
        }
    }

    companion object {
        const val DEFAULT_QUEUE_CAPACITY = 500
    }
}
