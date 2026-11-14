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
                val batch = takeBatch()
                if (batch.isEmpty()) continue
                sinkLock.lock()
                try {
                    for (line in batch) sink.appendLine(line)
                    maybeNoteDrops()
                    sink.flush(syncFileSystem = true)
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
        val snapshot = ArrayList<String>()
        synchronized(queue) {
            while (queue.isNotEmpty()) snapshot.add(queue.removeFirst())
        }
        val locked = if (timeoutMs == null) {
            sinkLock.lock()
            true
        } else {
            sinkLock.tryLock(timeoutMs, TimeUnit.MILLISECONDS)
        }
        if (!locked) return
        try {
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

    private fun takeBatch(): List<String> {
        synchronized(queue) {
            if (queue.isNotEmpty()) {
                val batch = ArrayList<String>(queue.size.coerceAtMost(64))
                while (queue.isNotEmpty() && batch.size < 64) {
                    batch.add(queue.removeFirst())
                }
                return batch
            }
        }
        if (!running.get()) return emptyList()
        synchronized(hasWork) {
            if (!peekQueued() && running.get()) {
                try {
                    hasWork.wait(250)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
            }
        }
        synchronized(queue) {
            if (queue.isEmpty()) return emptyList()
            val batch = ArrayList<String>(queue.size.coerceAtMost(64))
            while (queue.isNotEmpty() && batch.size < 64) {
                batch.add(queue.removeFirst())
            }
            return batch
        }
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
