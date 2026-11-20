package eu.komars.forgescanner.sync

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Sends the app's local text logs to the bot (POST /mobile/logs), so scans can be diagnosed without a USB cable:
 * Android/data/…/files/logs (resource scan, war/ranking events, quick skills…), files/profiles (read profiles)
 * and the small text diagnostics in the cache. Growing logs (*.jsonl) send only their new complete lines;
 * snapshot files (*.json, *.txt) are sent again when they change. Never images. Only when paired, never in DEMO.
 */
class LogUploader(context: Context, private val mobile: MobileSyncClient, private val demoMode: () -> Boolean) {
    private val app = context.applicationContext
    private val state = app.getSharedPreferences("log_upload", Context.MODE_PRIVATE)
    private val worker = Executors.newSingleThreadExecutor { Thread(it, "log-upload").apply { isDaemon = true } }
    private val running = AtomicBoolean(false)
    @Volatile private var lastRun = 0L

    /** Uploads what is new. [force]: at the end of a scan; otherwise at most every 30 s (e.g. on returning to the app). */
    fun flush(force: Boolean = false) {
        if (demoMode() || !mobile.paired) return
        val now = System.currentTimeMillis()
        if (!force && now - lastRun < 30_000) return
        if (!running.compareAndSet(false, true)) return
        lastRun = now
        worker.execute {
            try { runCatching { upload() } } finally { running.set(false) }
        }
    }

    private class Part(val path: String, val key: String, val append: Boolean, val content: String, val saved: String)

    private fun sources(): List<Pair<String, File>> {
        val result = mutableListOf<Pair<String, File>>()
        fun walk(prefix: String, dir: File?, depth: Int) {
            dir?.listFiles()?.sortedBy { it.name }?.forEach { file ->
                val path = "$prefix/${file.name}"
                if (file.isDirectory) { if (depth > 0) walk(path, file, depth - 1) }
                else if (accepted(path, file.length())) result += path to file
            }
        }
        val external = app.getExternalFilesDir(null)
        walk("ext/logs", external?.let { File(it, "logs") }, 1)
        walk("ext/profiles", external?.let { File(it, "profiles") }, 0)
        // The war/ranking event log already has its readable copy in ext/logs.
        app.cacheDir.listFiles()?.sortedBy { it.name }?.forEach { file ->
            if (file.isFile && file.name != "full_war-events.jsonl" && accepted("cache/${file.name}", file.length())) result += "cache/${file.name}" to file
        }
        return result
    }

    private fun upload() {
        val parts = sources().mapNotNull { (path, file) -> runCatching { part(path, file) }.getOrNull() }
        var batch = mutableListOf<Part>(); var size = 0; var requests = 0
        fun send() {
            if (batch.isEmpty() || requests >= MAX_REQUESTS) return
            val files = JSONArray()
            batch.forEach { files.put(JSONObject().put("path", it.path).put("append", it.append).put("content", it.content)) }
            mobile.logsBlocking(JSONObject().put("files", files))
            val edit = state.edit(); batch.forEach { edit.putString(it.key, it.saved) }; edit.apply()
            requests++; batch = mutableListOf(); size = 0
        }
        for (part in parts) {
            if (batch.size >= 30 || size + part.content.length > BATCH_CHARS) send()
            if (requests >= MAX_REQUESTS) break
            batch += part; size += part.content.length
        }
        send()
    }

    /** What to send for one file, or null when the server already has it. */
    private fun part(path: String, file: File): Part? {
        val length = file.length(); val modified = file.lastModified()
        if (length <= 0) return null
        val key = "f:$path"
        val previous = state.getString(key, null)?.split('|')
        val prefix = prefixHash(file)
        if (!path.endsWith(".jsonl")) {
            val saved = "$length|$modified|$prefix"
            if (state.getString(key, null) == saved) return null
            return Part(path, key, false, tail(file, 0L, length, wholeLines = false), saved)
        }
        val sentLength = previous?.getOrNull(0)?.toLongOrNull()
        val samePrefix = previous?.getOrNull(2) == prefix
        // Same file grown since the last upload: only the new complete lines. Rotated or trimmed: the whole tail again.
        val append = sentLength != null && samePrefix && length >= sentLength
        val from = if (append) sentLength!! else maxOf(0L, length - MAX_FILE)
        if (append && from == length) return null
        val text = tail(file, from, length, wholeLines = true)
        if (text.isEmpty()) return null
        val end = from + text.toByteArray(Charsets.UTF_8).size
        return Part(path, key, append, text, "$end|$modified|$prefix")
    }

    /** UTF-8 text of [from]..[until]; with [wholeLines] only up to the last newline (a line being written waits). */
    private fun tail(file: File, from: Long, until: Long, wholeLines: Boolean): String {
        val start = maxOf(from, until - MAX_FILE)
        val bytes = ByteArray((until - start).toInt())
        RandomAccessFile(file, "r").use { it.seek(start); it.readFully(bytes) }
        var first = 0
        if (start > from) first = bytes.indexOf('\n'.code.toByte()) + 1 // cut to a line start when the head was dropped
        var last = bytes.size
        if (wholeLines) last = bytes.lastIndexOf('\n'.code.toByte()) + 1
        if (last <= first) return ""
        return String(bytes, first, last - first, Charsets.UTF_8)
    }

    private fun prefixHash(file: File): String {
        val bytes = ByteArray(minOf(256L, file.length()).toInt())
        RandomAccessFile(file, "r").use { it.readFully(bytes) }
        return bytes.contentHashCode().toString(16)
    }

    companion object {
        const val MAX_FILE = 1_000_000L
        const val BATCH_CHARS = 900_000
        const val MAX_REQUESTS = 8
        private val NAME = Regex("^[A-Za-z0-9][A-Za-z0-9._-]{0,79}$")

        /** Same rules as the server: text files only, safe names, at most four path parts. */
        fun accepted(path: String, length: Long): Boolean {
            val parts = path.split('/')
            return length in 1..4_000_000 && path.length <= 160 && parts.size <= 4 && parts.all { NAME.matches(it) && !it.contains("..") } &&
                Regex("\\.(jsonl|json|txt)$", RegexOption.IGNORE_CASE).containsMatchIn(path)
        }
    }
}
