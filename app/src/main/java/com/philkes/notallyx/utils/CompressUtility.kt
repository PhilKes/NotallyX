package com.philkes.notallyx.utils

import android.provider.ContactsContract.CommonDataKinds.StructuredName.PREFIX
import android.util.Base64
import android.util.Log
import com.github.luben.zstd.Zstd
import com.philkes.notallyx.data.model.Converters
import com.philkes.notallyx.data.model.SpanRepresentation
import com.philkes.notallyx.utils.CompressUtility.COMPRESSION_THRESHOLD
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import org.json.JSONObject

/**
 * Shared compression utilities for large text payloads to decrease memory and storage usage.
 * - For EditText state (text + spans), we store a GZIP-compressed JSON as ByteArray.
 * - For BaseNote.body (String persisted in DB), we store GZIP(Base64) with a small prefix marker.
 */
object CompressUtility {

    // Threshold in characters for when to compress text (approximately 7KB)
    const val COMPRESSION_THRESHOLD: Int = 7_000

    // Prefix to mark a String as compressed (so we can store in a TEXT column).
    private const val PREFIX: String = "GZ:"

    // region Text + Spans (ByteArray)

    /** Compresses text and spans using GZIP compression into a ByteArray. */
    fun compressTextAndSpans(text: String, spans: List<SpanRepresentation>): ByteArray {
        val jsonObject = JSONObject()
        jsonObject.put("text", text)
        jsonObject.put("spans", Converters.spansToJSONArray(spans))
        val bytes = jsonObject.toString().toByteArray(Charsets.UTF_8)
        return Zstd.compress(bytes, 4)
    }

    /** Decompresses text and spans that were compressed with GZIP. */
    fun decompressTextAndSpans(compressedData: ByteArray): Pair<String, List<SpanRepresentation>> {
        val decompressedSize = Zstd.decompressedSize(compressedData)
        val result = ByteArray(decompressedSize.toInt())
        Zstd.decompress(result, compressedData)
        val jsonString = result.toString(Charsets.UTF_8)
        //        val bis = ByteArrayInputStream(compressedData)
        //        val jsonString = GZIPInputStream(bis).use { gzipIS ->
        //            gzipIS.readBytes().toString(Charsets.UTF_8)
        //        }
        val jsonObject = JSONObject(jsonString)
        val text = jsonObject.getString("text")
        val spansArray = jsonObject.getJSONArray("spans")
        val spans = Converters.jsonToSpans(spansArray)
        return Pair(text, spans)
    }

    // endregion

    // region String-only (BaseNote.body)

    /** Compress String if above threshold. Returns original if already small. */
    fun compressIfNeeded(text: String): String {
        if (text.length <= COMPRESSION_THRESHOLD) return text
        return try {
            val bos = ByteArrayOutputStream()
            GZIPOutputStream(bos).use { it.write(text.toByteArray(Charsets.UTF_8)) }
            val b64 = Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP)
            PREFIX + b64
        } catch (e: Exception) {
            Log.w("CompressUtility", "Failed to compress, returning original", e)
            text
        }
    }

    /** Decompress String if it was previously compressed with [compressIfNeeded]. */
    fun decompressIfNeeded(text: String): String {
        if (!text.startsWith(PREFIX)) return text
        val b64 = text.removePrefix(PREFIX)
        return try {
            val bytes = Base64.decode(b64, Base64.NO_WRAP)
            val bis = ByteArrayInputStream(bytes)
            GZIPInputStream(bis).use { it.readBytes().toString(Charsets.UTF_8) }
        } catch (e: Exception) {
            Log.w("CompressUtility", "Failed to decompress, returning original", e)
            text
        }
    }
    // endregion
}
