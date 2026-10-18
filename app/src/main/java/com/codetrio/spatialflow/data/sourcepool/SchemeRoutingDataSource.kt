package com.codetrio.spatialflow.data.sourcepool

import android.net.Uri
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import com.codetrio.spatialflow.data.deezer.DeezerDecryptingDataSource
import com.codetrio.spatialflow.data.tidal.TidalProgressiveDashDataSource
import okhttp3.OkHttpClient

import androidx.media3.datasource.TransferListener
import java.util.Locale

class SchemeRoutingDataSource(
    private val directFactory: DataSource.Factory,
    private val cachedFactory: DataSource.Factory,
    private val deezerFactory: DataSource.Factory,
    private val tidalDashFactory: DataSource.Factory
) : DataSource {

    private val transferListeners = mutableListOf<TransferListener>()
    private var delegate: DataSource? = null

    override fun addTransferListener(transferListener: TransferListener) {
        transferListeners += transferListener
        delegate?.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val scheme = dataSpec.uri.scheme?.lowercase(Locale.US)
        val selectedFactory = when {
            scheme == "content" || scheme == "file" || scheme == "android.resource" -> directFactory
            scheme == "deezer" -> deezerFactory
            scheme == "tidal-dash" -> tidalDashFactory
            scheme == "http" || scheme == "https" -> cachedFactory
            else -> directFactory
        }
        val selectedDataSource = selectedFactory.createDataSource()
        transferListeners.forEach(selectedDataSource::addTransferListener)
        delegate = selectedDataSource
        return selectedDataSource.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        delegate?.read(buffer, offset, length) ?: -1

    override fun getUri(): Uri? = delegate?.uri

    override fun getResponseHeaders(): Map<String, List<String>> =
        delegate?.responseHeaders ?: emptyMap()

    override fun close() {
        delegate?.close()
        delegate = null
    }

    class Factory(
        private val directFactory: DataSource.Factory,
        private val cachedFactory: DataSource.Factory,
        private val deezerFactory: DataSource.Factory,
        private val tidalDashFactory: DataSource.Factory
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource {
            return SchemeRoutingDataSource(
                directFactory = directFactory,
                cachedFactory = cachedFactory,
                deezerFactory = deezerFactory,
                tidalDashFactory = tidalDashFactory
            )
        }
    }
}
