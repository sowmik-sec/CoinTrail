package com.cointrail.data.sync.drive

import com.cointrail.data.sync.SyncRemoteStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID

/**
 * Stores the sync journal as one JSON file in the user's own Google Drive app-data folder
 * (`appDataFolder`). Because the folder lives in each user's Drive, per-account isolation is free:
 * two Google accounts can never read each other's file (SPEC §7).
 *
 * The Drive REST work is deliberately thin and untested — it is the transport, exactly like the
 * Play-Services sign-in flow — while the merge policy that guards the data is unit-tested separately.
 */
class GoogleDriveSyncRemoteStore(
    private val tokens: DriveAccessTokens,
) : SyncRemoteStore {

    override suspend fun readJournal(): String? = withContext(Dispatchers.IO) {
        val token = tokens.accessToken()
        val fileId = findFileId(token) ?: return@withContext null
        execute(token, "GET", "$FILES_ENDPOINT/$fileId?alt=media")
    }

    override suspend fun writeJournal(content: String): Unit = withContext(Dispatchers.IO) {
        val token = tokens.accessToken()
        val fileId = findFileId(token)
        if (fileId == null) {
            createFile(token, content)
        } else {
            execute(token, "PATCH", "$UPLOAD_ENDPOINT/$fileId?uploadType=media", content.toByteArray(), JSON_CONTENT_TYPE)
        }
    }

    private fun findFileId(token: String): String? {
        val query = URLEncoder.encode("name='$FILE_NAME'", "UTF-8")
        val response = execute(token, "GET", "$FILES_ENDPOINT?spaces=appDataFolder&pageSize=1&fields=files(id)&q=$query")
        val files = JSONObject(response).optJSONArray("files") ?: return null
        if (files.length() == 0) return null
        return files.getJSONObject(0).optString("id").takeIf { it.isNotEmpty() }
    }

    private fun createFile(token: String, content: String) {
        val boundary = "cointrail-${UUID.randomUUID()}"
        val metadata = JSONObject()
            .put("name", FILE_NAME)
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
        execute(
            token,
            "POST",
            "$UPLOAD_ENDPOINT?uploadType=multipart",
            body.toByteArray(),
            "multipart/related; boundary=$boundary",
        )
    }

    private fun execute(
        token: String,
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

    companion object {
        /** The one file that holds the merged journal for a signed-in account. */
        const val FILE_NAME: String = "cointrail-sync-journal.json"

        // Metadata/media reads use the Drive v3 host; uploads must put `upload` before `drive/v3`.
        private const val FILES_ENDPOINT: String = "https://www.googleapis.com/drive/v3/files"
        private const val UPLOAD_ENDPOINT: String = "https://www.googleapis.com/upload/drive/v3/files"
        private const val APP_DATA_FOLDER: String = "appDataFolder"
        private const val JSON_CONTENT_TYPE: String = "application/json; charset=UTF-8"
        private const val CONNECT_TIMEOUT_MS: Int = 15_000
        private const val READ_TIMEOUT_MS: Int = 30_000
    }
}
