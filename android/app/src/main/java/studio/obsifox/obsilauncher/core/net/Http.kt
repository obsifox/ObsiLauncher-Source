package studio.obsifox.obsilauncher.core.net

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Minimal HTTP layer: text fetch + resumable-ish file download with progress and sha1 check. */
object Http {

    private const val CONNECT_TIMEOUT = 15_000
    private const val READ_TIMEOUT = 30_000
    private const val ATTEMPTS = 3

    fun get(url: String): String? = repeatable {
        val conn = open(url)
        try {
            if (conn.responseCode !in 200..299) throw IOException("HTTP ${conn.responseCode} for $url")
            conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    fun downloadToFile(
        url: String,
        dest: File,
        sha1: String? = null,
        progress: (done: Long, total: Long) -> Unit = { _, _ -> },
    ) {
        require(!dest.isDirectory) { "destination is a directory: $dest" }
        if (sha1 != null && dest.isFile && dest.length() > 0 && sha1Of(dest) == sha1) {
            progress(dest.length(), dest.length())
            return
        }
        dest.parentFile?.mkdirs()
        val tmp = File(dest.absolutePath + ".part")
        repeatable {
            val conn = open(url)
            try {
                if (conn.responseCode !in 200..299) throw IOException("HTTP ${conn.responseCode} for $url")
                val total = conn.contentLengthLong.let { if (it > 0) it else -1L }
                var done = 0L
                conn.inputStream.use { input ->
                    tmp.outputStream().use { output ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            output.write(buf, 0, n)
                            done += n
                            progress(done, total)
                        }
                    }
                }
            } finally {
                conn.disconnect()
            }
        }
        if (sha1 != null) {
            val actual = sha1Of(tmp)
            if (!actual.equals(sha1, ignoreCase = true)) {
                tmp.delete()
                throw IOException("sha1 mismatch for $url (want $sha1, got $actual)")
            }
        }
        if (dest.exists()) dest.delete()
        if (!tmp.renameTo(dest)) throw IOException("cannot move ${tmp.name} into place")
    }

    private inline fun <T> repeatable(block: () -> T): T {
        var last: Exception? = null
        repeat(ATTEMPTS) { attempt ->
            if (attempt > 0) Thread.sleep(800L * attempt)
            try {
                return block()
            } catch (e: Exception) {
                last = e
            }
        }
        throw last ?: IOException("download failed")
    }

    private fun open(url: String): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = CONNECT_TIMEOUT
        conn.readTimeout = READ_TIMEOUT
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("User-Agent", "ObsiLauncher/1.12.0 (free, GPL-3.0)")
        conn.setRequestProperty("Accept", "application/json, */*")
        return conn
    }

    fun sha1Of(file: File): String {
        val md = MessageDigest.getInstance("SHA-1")
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    fun enc(value: String): String = java.net.URLEncoder.encode(value, Charsets.UTF_8.name())
        .replace("+", "%20")

    /** POST a JSON body, return the JSON text response (Modrinth bulk endpoints). */
    fun postJson(url: String, body: String): String = repeatable {
        val conn = open(url)
        try {
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Accept", "application/json")
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            if (conn.responseCode !in 200..299) throw IOException("HTTP ${conn.responseCode} for $url")
            conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    /** GET JSON with a bearer token (Minecraft services). */
    fun authedJson(url: String, token: String): String? = repeatable {
        val conn = open(url)
        try {
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.setRequestProperty("Accept", "application/json")
            if (conn.responseCode !in 200..299) throw IOException("HTTP ${conn.responseCode} for $url")
            conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
