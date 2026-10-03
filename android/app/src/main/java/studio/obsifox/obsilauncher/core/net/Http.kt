package studio.obsifox.obsilauncher.core.net

import studio.obsifox.obsilauncher.BuildConfig
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Minimal HTTP layer:
 *
 *  - [get] is EXPLICITLY throwing — use it where a failure must stop the flow;
 *    [getOrNull] exists for call sites that carry their own fallback
 *    (the old `get(): String?` never actually returned null, so every
 *    `?: fallback` after it was dead code).
 *  - GitHub-style rate limiting (429, or 403 with X-RateLimit-Remaining: 0)
 *    raises [RateLimited]; Retry-After / X-RateLimit-Reset are honoured and
 *    the retry loop gives up early when the wait window exceeds 10 s.
 *  - [downloadToFile] is truly resumable: it keeps the `.part` file, asks the
 *    server with `Range: bytes=N-`, appends via RandomAccessFile, accepts 206
 *    and restarts from zero only when the server ignores the range.
 */
object Http {

    private const val CONNECT_TIMEOUT = 15_000
    private const val READ_TIMEOUT = 30_000
    private const val ATTEMPTS = 3

    /** never sit longer than this waiting for a rate-limit window */
    private const val MAX_RATE_WAIT_MS = 10_000L

    /** the server is throttling us (429 / quota-exhausted 403) */
    class RateLimited(val retryAfterMs: Long?, message: String) : IOException(message)

    fun get(url: String): String = repeatable { fetchText(url) }

    /** same as [get] but every failure (incl. rate limits) becomes null — for fallback paths */
    fun getOrNull(url: String): String? = try {
        get(url)
    } catch (_: Exception) {
        null
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
        var last: Exception? = null
        for (attempt in 0 until ATTEMPTS) {
            if (attempt > 0) Thread.sleep(800L * attempt)
            try {
                val have = if (tmp.isFile) tmp.length() else 0L
                val conn = open(url)
                val resumed: Boolean
                try {
                    if (have > 0L) conn.setRequestProperty("Range", "bytes=$have-")
                    val code = conn.responseCode
                    throwIfRateLimited(conn, code, url)
                    when {
                        // server honours the range — append to what we already have
                        code == 206 && have > 0L -> resumed = true
                        // full body (or a bogus 206 for a fresh file) — start clean
                        code in 200..299 -> {
                            resumed = false
                            if (tmp.isFile) tmp.delete()
                        }
                        // the .part already holds everything the server could send
                        code == 416 && have > 0L -> {
                            conn.disconnect()
                            finishDownload(tmp, dest, sha1, progress)
                            return
                        }
                        else -> throw IOException("HTTP $code for $url")
                    }
                    val base = conn.contentLengthLong.let { if (it > 0) it else -1L }
                    val total = if (base > 0) base + if (resumed) have else 0L else -1L
                    conn.inputStream.use { input ->
                        RandomAccessFile(tmp, "rw").use { output ->
                            if (resumed) output.seek(have) else output.setLength(0)
                            val buf = ByteArray(64 * 1024)
                            var done = if (resumed) have else 0L
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
                finishDownload(tmp, dest, sha1, progress)
                return
            } catch (e: RateLimited) {
                last = e
                val wait = e.retryAfterMs
                    // no usable window hint — stop hammering a throttled server
                    ?: break
                if (wait <= 0 || wait > MAX_RATE_WAIT_MS) break
                Thread.sleep(wait)
            } catch (e: Exception) {
                last = e
                // keep the .part — the next attempt resumes from where we stopped
            }
        }
        throw last ?: IOException("download failed: $url")
    }

    private fun finishDownload(tmp: File, dest: File, sha1: String?, progress: (Long, Long) -> Unit) {
        if (sha1 != null) {
            val actual = sha1Of(tmp)
            if (!actual.equals(sha1, ignoreCase = true)) {
                tmp.delete() // corrupt — never resume from it
                throw IOException("sha1 mismatch for ${dest.name} (want $sha1, got $actual)")
            }
        }
        if (dest.exists()) dest.delete()
        if (!tmp.renameTo(dest)) throw IOException("cannot move ${tmp.name} into place")
        progress(dest.length(), dest.length())
    }

    private fun fetchText(url: String): String {
        val conn = open(url)
        try {
            val code = conn.responseCode
            throwIfRateLimited(conn, code, url)
            if (code !in 200..299) throw IOException("HTTP $code for $url")
            return conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun throwIfRateLimited(conn: HttpURLConnection, code: Int, url: String) {
        val quotaGone = code == 403 && "0" == conn.getHeaderField("X-RateLimit-Remaining")
        if (code != 429 && !quotaGone) return
        val now = System.currentTimeMillis()
        val waitMs = conn.getHeaderField("Retry-After")?.trim()?.toLongOrNull()?.let { it * 1000L }
            ?: conn.getHeaderField("X-RateLimit-Reset")?.trim()?.toLongOrNull()?.let { reset ->
                // GitHub sends epoch UTC seconds here
                if (reset > 10_000_000_000L) reset * 1000L - now else reset * 1000L
            }?.takeIf { it > 0 }
        throw RateLimited(
            waitMs?.takeIf { it <= MAX_RATE_WAIT_MS },
            "rate limited by server (HTTP ${code}) for $url" +
                (waitMs?.let { " — retry in ~${it / 1000}s" } ?: " — quota window too long to wait"),
        )
    }

    private fun <T> repeatable(block: () -> T): T {
        var last: Exception? = null
        for (attempt in 0 until ATTEMPTS) {
            if (attempt > 0) Thread.sleep(800L * attempt)
            try {
                return block()
            } catch (e: RateLimited) {
                last = e
                val wait = e.retryAfterMs ?: break
                if (wait <= 0 || wait > MAX_RATE_WAIT_MS) break
                Thread.sleep(wait)
            } catch (e: Exception) {
                last = e
            }
        }
        throw last ?: IOException("request failed")
    }

    private fun open(url: String): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = CONNECT_TIMEOUT
        conn.readTimeout = READ_TIMEOUT
        conn.instanceFollowRedirects = true
        // Modrinth requires a UA that identifies the client and a way to reach it
        conn.setRequestProperty(
            "User-Agent",
            "ObsiLauncher/${BuildConfig.VERSION_NAME} (Android; +https://github.com/obsifox/ObsiLauncher-Source)",
        )
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
            val code = conn.responseCode
            throwIfRateLimited(conn, code, url)
            if (code !in 200..299) throw IOException("HTTP $code for $url")
            conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    /** GET JSON with a bearer token (Minecraft services) — null on ANY failure. */
    fun authedJson(url: String, token: String): String? = try {
        repeatable {
            val conn = open(url)
            try {
                conn.setRequestProperty("Authorization", "Bearer $token")
                conn.setRequestProperty("Accept", "application/json")
                val code = conn.responseCode
                throwIfRateLimited(conn, code, url)
                if (code !in 200..299) throw IOException("HTTP $code for $url")
                conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            } finally {
                conn.disconnect()
            }
        }
    } catch (_: Exception) {
        null
    }
}
