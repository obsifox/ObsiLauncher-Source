package studio.obsifox.launcher.core.net

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import studio.obsifox.launcher.core.util.toHex
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ProxySelector
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.Duration

class HttpException(val status: Int, val url: String, val body: String) :
    IOException("HTTP $status for $url" + if (body.isNotBlank()) ": ${body.take(300)}" else "")

@Serializable
enum class MirrorPreset { OFFICIAL, BMCLAPI }

@Serializable
data class NetConfig(
    val mirror: MirrorPreset = MirrorPreset.OFFICIAL,
    val proxyHost: String? = null,
    val proxyPort: Int? = null,
)

data class HttpResult(val status: Int, val body: String)

/**
 * Thin coroutine-friendly wrapper around the JDK HTTP client.
 * Knows about download mirrors (useful where Mojang / GitHub are filtered) and an optional HTTP proxy.
 */
class Http(private val userAgent: String, config: NetConfig = NetConfig()) {
    @Volatile
    private var client: HttpClient = build(config)

    @Volatile
    var config: NetConfig = config
        set(value) {
            field = value
            client = build(value)
        }

    private fun build(c: NetConfig): HttpClient {
        val b = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(20))
        if (!c.proxyHost.isNullOrBlank() && c.proxyPort != null && c.proxyPort > 0) {
            b.proxy(ProxySelector.of(InetSocketAddress(c.proxyHost, c.proxyPort)))
        }
        return b.build()
    }

    /** Candidate URLs for [url]: mirror first (when enabled and known), then the original. */
    fun candidates(url: String): List<String> {
        if (config.mirror == MirrorPreset.OFFICIAL) return listOf(url)
        val m = Mirrors.bmclapi(url)
        return if (m != null) listOf(m, url) else listOf(url)
    }

    private fun request(method: String, url: String, headers: Map<String, String>, body: String?, contentType: String?): HttpRequest {
        val b = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(60))
            .header("User-Agent", userAgent)
            .header("Accept", "application/json, */*")
        headers.forEach { (k, v) -> b.header(k, v) }
        if (contentType != null) b.header("Content-Type", contentType)
        when (method) {
            "GET" -> b.GET()
            else -> b.method(method, if (body == null) HttpRequest.BodyPublishers.noBody() else HttpRequest.BodyPublishers.ofString(body))
        }
        return b.build()
    }

    /** Raw request without status checking (used by auth flows that inspect error bodies). */
    suspend fun raw(
        method: String,
        url: String,
        headers: Map<String, String> = emptyMap(),
        body: String? = null,
        contentType: String? = null,
    ): HttpResult = withContext(Dispatchers.IO) {
        val resp = client.sendAsync(request(method, url, headers, body, contentType), HttpResponse.BodyHandlers.ofString()).await()
        HttpResult(resp.statusCode(), resp.body() ?: "")
    }

    private suspend fun checked(
        method: String,
        url: String,
        headers: Map<String, String>,
        body: String?,
        contentType: String?,
        mirrors: Boolean,
    ): String {
        val urls = if (mirrors) candidates(url) else listOf(url)
        var last: Throwable? = null
        for (attempt in 0 until 3) {
            var retryable = false
            for (u in urls) {
                try {
                    val r = raw(method, u, headers, body, contentType)
                    if (r.status in 200..299) return r.body
                    last = HttpException(r.status, u, r.body)
                    if (r.status >= 500 || r.status == 429) retryable = true
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    last = e
                    retryable = true
                }
            }
            if (!retryable) break
            delay(400L * (attempt + 1))
        }
        throw last ?: IOException("Request failed: $url")
    }

    suspend fun getString(url: String, headers: Map<String, String> = emptyMap(), mirrors: Boolean = true): String =
        checked("GET", url, headers, null, null, mirrors)

    suspend fun postJson(url: String, json: String, headers: Map<String, String> = emptyMap()): String =
        checked("POST", url, headers, json, "application/json", false)

    suspend fun postForm(url: String, form: Map<String, String>): HttpResult =
        raw("POST", url, emptyMap(), form.entries.joinToString("&") { "${enc(it.key)}=${enc(it.value)}" }, "application/x-www-form-urlencoded")

    suspend fun getBytes(url: String, maxBytes: Int = 8 * 1024 * 1024): ByteArray = withContext(Dispatchers.IO) {
        val resp = client.sendAsync(request("GET", url, emptyMap(), null, null), HttpResponse.BodyHandlers.ofByteArray()).await()
        if (resp.statusCode() !in 200..299) throw HttpException(resp.statusCode(), url, "")
        val b = resp.body()
        if (b.size > maxBytes) throw IOException("Response too large: $url")
        b
    }

    /**
     * Downloads to [dest] (via a .part file), optionally verifying SHA-1.
     * Tries every mirror candidate, 3 rounds. [onBytes] receives positive increments (negative on rollback).
     */
    suspend fun download(
        urls: List<String>,
        dest: Path,
        sha1: String? = null,
        onBytes: (Long) -> Unit = {},
    ) = withContext(Dispatchers.IO) {
        val all = urls.flatMap { candidates(it) }.distinct()
        var last: Throwable? = null
        for (attempt in 0 until 3) {
            for (u in all) {
                try {
                    downloadOnce(u, dest, sha1, onBytes)
                    return@withContext
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    last = e
                }
            }
            delay(500L * (attempt + 1))
        }
        throw IOException("Failed to download ${urls.firstOrNull()}: ${last?.message}", last)
    }

    private suspend fun downloadOnce(url: String, dest: Path, sha1: String?, onBytes: (Long) -> Unit) {
        Files.createDirectories(dest.parent)
        val tmp = dest.resolveSibling(dest.fileName.toString() + ".part")
        val req = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofMinutes(15))
            .header("User-Agent", userAgent)
            .GET().build()
        val resp = client.sendAsync(req, HttpResponse.BodyHandlers.ofInputStream()).await()
        if (resp.statusCode() !in 200..299) {
            resp.body().close()
            throw HttpException(resp.statusCode(), url, "")
        }
        val md = sha1?.let { MessageDigest.getInstance("SHA-1") }
        var counted = 0L
        try {
            resp.body().use { input ->
                Files.newOutputStream(tmp).use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        md?.update(buf, 0, n)
                        counted += n
                        onBytes(n.toLong())
                    }
                }
            }
            if (md != null) {
                val got = md.digest().toHex()
                if (!got.equals(sha1, ignoreCase = true)) throw IOException("SHA-1 mismatch for $url (expected $sha1, got $got)")
            }
            try {
                Files.move(tmp, dest, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: Exception) {
                Files.move(tmp, dest, StandardCopyOption.REPLACE_EXISTING)
            }
        } catch (e: Throwable) {
            Files.deleteIfExists(tmp)
            onBytes(-counted)
            throw e
        }
    }

    companion object {
        fun enc(s: String): String = URLEncoder.encode(s, Charsets.UTF_8)
    }
}

/** URL rewriting for BMCLAPI (https://bmclapi2.bangbang93.com), a public mirror of Mojang / Forge / Fabric endpoints. */
object Mirrors {
    private const val B = "https://bmclapi2.bangbang93.com"
    private val rules = listOf(
        "https://launchermeta.mojang.com" to B,
        "https://piston-meta.mojang.com" to B,
        "https://piston-data.mojang.com" to B,
        "https://launcher.mojang.com" to B,
        "https://libraries.minecraft.net" to "$B/maven",
        "https://resources.download.minecraft.net" to "$B/assets",
        "https://maven.minecraftforge.net" to "$B/maven",
        "https://files.minecraftforge.net/maven" to "$B/maven",
        "https://maven.neoforged.net/releases" to "$B/maven",
        "https://meta.fabricmc.net" to "$B/fabric-meta",
        "https://maven.fabricmc.net" to "$B/maven",
    )

    fun bmclapi(url: String): String? =
        rules.firstOrNull { url.startsWith(it.first) }?.let { it.second + url.removePrefix(it.first) }
}
