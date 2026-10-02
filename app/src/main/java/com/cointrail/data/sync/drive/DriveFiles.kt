package com.cointrail.data.sync.drive

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID

/** A file in the user's Drive app-data folder, reduced to the fields CoinTrail needs. */
internal data class DriveFile(val id: String, val name: String)

/**
 * Thin Drive v3 REST access to the app's own hidden `appDataFolder` (SPEC §7). Both the sync journal
 * and the backup snapshots live there, so the two stores share this one transport.
 *
 * Like the rest of the Drive layer this is deliberately thin and untested — it is the transport,
 * exactly as the Play-Services sign-in flow is — while the data policy it carries (merge, backup
 * format) is unit-tested separately. All calls run on the caller's dispatcher and hold a single
 * short-lived access token, which the caller obtains before constructing this.
 */
internal class DriveFiles(private val token: String) {

    /** The file with exactly [name], or null. Used for the one-per-account sync journal. */
    fun findByName(name: String): DriveFile? =
        listByQuery("name='${escapeQueryValue(name)}'").firstOrNull()

    /** Every file whose name begins with [prefix]; used to enumerate timestamped backup snapshots. */
    fun listByPrefix(prefix: String): List<DriveFile> =
        listByQuery("name contains '${escapeQueryValue(prefix)}'")

    fun read(fileId: String): String =
        execute("GET", "$FILES_ENDPOINT/$fileId?alt=media")

    fun create(name: String, content: String): DriveFile {
        val boundary = "cointrail-${UUID.randomUUID()}"
        val metadata = JSONObject()
            .put("name", name)
            .put("parents", listOf(APP_DATA_FOLDER))
            .toString()
        val body = buildString {
            append("--$boundary\r\n")
            append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
            append(metadata).append("\r\n")
            append("--$boundary\r\n")
            append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
            append(content).append("\r\n")
            append("--$boundary--\r\n")
        }
        val response = execute(
            "POST",
            "$UPLOAD_ENDPOINT?uploadType=multipart",
            body.toByteArray(),
            "multipart/related; boundary=$boundary",
        )
        val json = JSONObject(response)
        return DriveFile(json.getString("id"), json.optString("name", name))
    }

    fun update(fileId: String, content: String) {
        execute(
            "PATCH",
            "$UPLOAD_ENDPOINT/$fileId?uploadType=media",
            content.toByteArray(),
            JSON_CONTENT_TYPE,
        )
    }

    private fun listByQuery(query: String): List<DriveFile> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val response = execute("GET", "$FILES_ENDPOINT?spaces=appDataFolder&pageSize=1000&fields=files(id,name)&q=$encoded")
        val files = JSONObject(response).optJSONArray("files") ?: return emptyList()
        return (0 until files.length()).map { index ->
            val file = files.getJSONObject(index)
            DriveFile(file.getString("id"), file.optString("name"))
        }
    }

    private fun execute(
        method: String,
        url: String,
        body: ByteArray? = null,
        contentType: String? = null,
    ): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            setRequestProperty("Authorization", "Bearer $token")
            if (contentType != null) setRequestProperty("Content-Type", contentType)
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            doInput = true
            if (body != null) {
                doOutput = true
                setFixedLengthStreamingMode(body.size)
            }
        }
        try {
            if (body != null) connection.outputStream.use { it.write(body) }

            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw IOException("Google Drive request failed ($code): $text")
            return text
        } finally {
            connection.disconnect()
        }
    }

    private fun escapeQueryValue(value: String): String =
        value.replace("\\", "\\\\").replace("'", "\\'")

    companion object {
        // Metadata/media reads use the Drive v3 host; uploads must put `upload` before `drive/v3`.
        private const val FILES_ENDPOINT: String = "https://www.googleapis.com/drive/v3/files"
        private const val UPLOAD_ENDPOINT: String = "https://www.googleapis.com/upload/drive/v3/files"
        private const val APP_DATA_FOLDER: String = "appDataFolder"
        private const val JSON_CONTENT_TYPE: String = "application/json; charset=UTF-8"
        private const val CONNECT_TIMEOUT_MS: Int = 15_000
        private const val READ_TIMEOUT_MS: Int = 30_000
    }
}
