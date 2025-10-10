package com.grindrplus.core

import android.content.ContentUris
import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import com.grindrplus.GrindrPlus.context
import okhttp3.Request
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object HttpLogger {

    /**
     * Logs the details of an HTTP request and its corresponding response.
     * This function constructs a formatted log message and uses MediaStore to save it.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    fun log(request: Request, response: Response) {
        try {
            val timestamp =
                SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            val logMessage = buildString {
                append("[$timestamp]\n")
                append("--- REQUEST -->\n")
                append("  ${request.method} ${request.url}\n")
                append("  Headers:\n")
                append(formatHeaders(request.headers))
                append("\n\n")
                append("<-- RESPONSE ---\n")
                append("  ${response.code} ${response.message}\n")
                append("  Headers:\n")
                append(formatHeaders(response.headers))
                append("\n----------------------------------------\n\n")
            }
            // Use MediaStore to write to the log file
            writeToLogFile(logMessage)

        } catch (e: Exception) {
            Logger.e("Failed to write to HTTP log file: ${e.message}")
        }
    }

    /**
     * Formats HTTP headers into a readable string.
     */
    private fun formatHeaders(headers: okhttp3.Headers): String {
        return headers.toMultimap().entries.joinToString("\n") { (key, values) ->
            "     $key: ${values.joinToString(", ")}"
        }
    }

    /**
     * Writes the log message to the "GrindrPlus_HttpLogs.txt" file in the device's
     * public Downloads directory using the MediaStore API for modern Android versions.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun writeToLogFile(logMessage: String) {
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "GrindrPlus_HttpLogs.txt")
            put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }

        val resolver = context.contentResolver
        // Find existing log file URI or create a new one
        val uri = resolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Downloads._ID),
            "${MediaStore.MediaColumns.DISPLAY_NAME} = ?",
            arrayOf("GrindrPlus_HttpLogs.txt"),
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID))
                ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id)
            } else {
                resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            }
        } ?: resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)

        // Open an OutputStream to append the text to the file
        uri?.let {
            resolver.openOutputStream(it, "wa")?.use { outputStream -> // 'wa' = write-append
                outputStream.write(logMessage.toByteArray())
            }
        }
    }
}