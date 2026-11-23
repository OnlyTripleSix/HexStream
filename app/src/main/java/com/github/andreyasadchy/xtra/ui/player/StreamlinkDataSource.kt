package com.github.andreyasadchy.xtra.ui.player

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSpec
import com.chaquo.python.PyException
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import java.io.IOException

/** Bridges Streamlink's filtered, progressive output straight into Media3. */
@OptIn(UnstableApi::class)
internal class StreamlinkDataSource(
    private val context: Context,
    private val channel: String,
    private val preferredQuality: String?,
    private val proxyUrls: List<String>,
    private val allowDirect: Boolean,
) : BaseDataSource(true) {
    private var stream: PyObject? = null
    private var uri: Uri? = null
    private var receivedData = false

    override fun open(dataSpec: DataSpec): Long {
        if (dataSpec.position != 0L) throw IOException("Streamlink live output cannot seek")
        transferInitializing(dataSpec)
        val reader = try {
            synchronized(pythonLock) {
                if (!Python.isStarted()) Python.start(AndroidPlatform(context.applicationContext))
            }
            Python.getInstance().getModule("hexstream_streamlink").callAttr(
                "open_stream", channel, preferredQuality, proxyUrls.toTypedArray(), allowDirect,
            )
        } catch (error: PyException) {
            throw streamlinkError("open", error)
        } catch (error: RuntimeException) {
            throw streamlinkError("start", error)
        } catch (error: LinkageError) {
            throw streamlinkError("runtime", error)
        }
        stream = reader
        uri = dataSpec.uri
        receivedData = false
        transferStarted(dataSpec)
        return C.LENGTH_UNSET.toLong()
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        val reader = stream ?: throw IOException("Streamlink output is closed")
        val bytes = try {
            reader.callAttr("read", length).toJava(ByteArray::class.java)
        } catch (error: PyException) {
            throw streamlinkError("read", error)
        }
        if (bytes.isEmpty()) return C.RESULT_END_OF_INPUT
        if (!receivedData) {
            receivedData = true
            Log.i(TAG, "stream_bytes_received")
        }
        bytes.copyInto(buffer, offset)
        bytesTransferred(bytes.size)
        return bytes.size
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        val reader = stream ?: return
        stream = null
        uri = null
        try {
            reader.callAttr("close")
        } catch (_: PyException) {
            Log.w(TAG, "close_failed")
        } finally {
            reader.close()
            transferEnded()
        }
    }

    private fun streamlinkError(operation: String, error: Throwable): IOException {
        // Python errors may contain signed playlists or proxy information.
        val kind = if (error is PyException) {
            error.message?.substringBefore(':')?.takeIf { it.matches(Regex("[A-Za-z.]+")) }
                ?: "PythonError"
        } else error.javaClass.simpleName
        Log.w(TAG, "failure operation=$operation kind=$kind")
        return IOException("Streamlink $operation failed ($kind)")
    }

    companion object {
        const val TAG = "HexStreamStreamlink"
        private val pythonLock = Any()
    }
}
