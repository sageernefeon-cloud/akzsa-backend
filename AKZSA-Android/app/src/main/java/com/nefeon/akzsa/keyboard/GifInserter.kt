package com.nefeon.akzsa.keyboard

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.core.content.FileProvider
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Sends a GIF into the focused chat field through the keyboard-image API (commitContent).
 * If the app does not accept GIFs from keyboards, the GIF link is copied to the clipboard instead
 * (the user can paste it; most chat apps turn a GIPHY link into a preview).
 */
object GifInserter {
    private const val MAX_BYTES = 6L * 1024 * 1024
    private const val MAX_CACHED_FILES = 20

    enum class Outcome { SENT, LINK_COPIED, FAILED }

    /** True when the focused editor advertises image/gif (or a wildcard image type). */
    fun editorAcceptsGif(editor: EditorInfo?): Boolean {
        if (editor == null) return false
        return EditorInfoCompat.getContentMimeTypes(editor).any {
            ClipDescription.compareMimeTypes("image/gif", it)
        }
    }

    /** Blocking: call from Dispatchers.IO. Returns the cached file, or null when the download fails. */
    fun download(context: Context, url: String): File? {
        if (!url.startsWith("https://")) return null
        return try {
            val dir = File(context.cacheDir, "gifs").apply { mkdirs() }
            dir.listFiles()?.sortedBy { it.lastModified() }?.dropLast(MAX_CACHED_FILES - 1)?.forEach { it.delete() }
            val out = File(dir, "gif_${System.currentTimeMillis()}.gif")
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 12000
            try {
                if (conn.responseCode !in 200..299) return null
                if (conn.contentLengthLong > MAX_BYTES) return null
                var total = 0L
                conn.inputStream.use { input ->
                    out.outputStream().use { output ->
                        val buf = ByteArray(16 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            total += n
                            if (total > MAX_BYTES) { out.delete(); return null }
                            output.write(buf, 0, n)
                        }
                    }
                }
                if (total <= 0) { out.delete(); null } else out
            } finally { conn.disconnect() }
        } catch (_: Exception) { null }
    }

    /** Main thread. Commits the downloaded file to the editor. Returns false if the app refused it. */
    fun commit(context: Context, ic: InputConnection?, editor: EditorInfo?, file: File, title: String): Boolean {
        if (ic == null || editor == null) return false
        return try {
            val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val content = InputContentInfoCompat(uri, ClipDescription(title.ifBlank { "GIF" }, arrayOf("image/gif")), null)
            InputConnectionCompat.commitContent(
                ic, editor, content,
                InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION,
                Bundle(),
            )
        } catch (_: Exception) { false }
    }

    fun copyLink(context: Context, url: String, title: String): Boolean = try {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(title.ifBlank { "GIF" }, url))
        true
    } catch (_: Exception) { false }
}
