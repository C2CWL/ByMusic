package com.sosauce.chocola.core.data.online

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener

/**
 * Creates the real DataSource only when it is first opened.
 *
 * DefaultDataSource builds its "remote" source eagerly for every item, including local
 * files. Wrapping the network + cache stack in this class means local playback never
 * creates the OkHttp client or opens the playback cache.
 */
@OptIn(UnstableApi::class)
class LazyDataSource(private val create: () -> DataSource) : DataSource {

    private var delegate: DataSource? = null
    private val listeners = ArrayList<TransferListener>()

    override fun addTransferListener(transferListener: TransferListener) {
        listeners += transferListener
        delegate?.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val source = delegate ?: create().also { created ->
            listeners.forEach(created::addTransferListener)
            delegate = created
        }
        return source.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        checkNotNull(delegate) { "read() before open()" }.read(buffer, offset, length)

    override fun getUri(): Uri? = delegate?.uri

    override fun getResponseHeaders(): Map<String, List<String>> =
        delegate?.responseHeaders ?: emptyMap()

    override fun close() {
        delegate?.close()
    }
}
