package studio.obsifox.obsilauncher.core.net

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException

/**
 * Minimal pytube-style YouTube stream resolver, written from scratch for
 * ObsiLauncher's live-video background (no dependencies, no API keys —
 * the same InnerTube player-endpoint approach the pytube project uses).
 *
 * It resolves a direct mp4 stream URL for the trailer video the user pinned
 * in the 1.3.0 brief (https://youtu.be/1HCrV7mFWr8) and downloads it for the
 * looping, muted background. Several InnerTube clients are tried in order;
 * the first one that returns a clean (non-ciphered) mp4 stream wins.
 */
object YouTube {

    /** The pinned official trailer used as the live background. */
    const val TRAILER_VIDEO_ID = "1HCrV7mFWr8"

    data class Stream(
        val url: String,
        val width: Int,
        val height: Int,
        val sizeBytes: Long,
        val progressive: Boolean,
    )

    data class Result(val title: String, val stream: Stream)

    private const val ENDPOINT = "https://www.youtube.com/youtubei/v1/player?prettyPrint=false"

    /** clientName, clientVersion, x-client-name integer, extra client fields */
    private data class Client(
        val name: String,
        val version: String,
        val id: Int,
        val userAgent: String,
        val extra: String,
    )

    private val CLIENTS = listOf(
        Client(
            "ANDROID", "19.09.37", 3,
            "com.google.android.youtube/19.09.37 (Linux; U; Android 11) gzip",
            "\"androidSdkVersion\":30,\"osName\":\"Android\",\"osVersion\":\"11\",\"hl\":\"en\",\"gl\":\"US\"",
        ),
        Client(
            "IOS", "19.09.3", 5,
            "com.google.ios.youtube/19.09.3 (iPhone14,3; U; CPU iOS 17_2_1 like Mac OS X)",
            "\"deviceMake\":\"Apple\",\"deviceModel\":\"iPhone14,3\",\"osName\":\"iPhone\",\"osVersion\":\"17.2.1.21C66\",\"hl\":\"en\"",
        ),
        Client(
            "WEB", "2.20240304.00.00", 1,
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0 Safari/537.36",
            "\"hl\":\"en\"",
        ),
    )

    /** Resolve the best playable mp4 stream for [videoId]. */
    fun resolve(videoId: String = TRAILER_VIDEO_ID): Result {
        var lastError: Exception? = null
        for (client in CLIENTS) {
            try {
                val body = JSONObject().apply {
                    put(
                        "context",
                        JSONObject().put(
                            "client",
                            JSONObject().apply {
                                put("clientName", client.name)
                                put("clientVersion", client.version)
                                // extra fields live flat inside the client object
                                val extras = JSONObject("{${client.extra}}")
                                extras.keys().forEach { key -> put(key, extras.get(key)) }
                            },
                        ),
                    )
                    put("videoId", videoId)
                    put("contentCheckOk", true)
                    put("racyCheckOk", true)
                }
                val json = postInnertube(body, client)
                val status = json.optJSONObject("playabilityStatus")?.optString("status").orEmpty()
                if (status != "OK") throw IOException("playabilityStatus=$status")
                val title = json.optJSONObject("videoDetails")?.optString("title") ?: videoId
                val streaming = json.optJSONObject("streamingData")
                    ?: throw IOException("no streamingData in response")
                val best = pickBest(streaming) ?: throw IOException("no clean mp4 stream returned")
                return Result(title, best)
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: IOException("YouTube resolve failed")
    }

    /** Resolve + download to [dest] with byte progress (useful for a progress row). */
    fun download(
        videoId: String,
        dest: File,
        progress: (done: Long, total: Long) -> Unit = { _, _ -> },
    ): Result {
        val result = resolve(videoId)
        progress(0L, result.stream.sizeBytes)
        Http.downloadToFile(result.stream.url, dest) { done, total -> progress(done, total) }
        return result
    }

    // ---- internals -------------------------------------------------------------

    private fun pickBest(streaming: JSONObject): Stream? {
        // Prefer a progressive mp4 (video+audio in one file); a muted background
        // does not strictly need audio, so a video-only adaptive stream is fine too.
        bestOf(streaming.optJSONArray("formats"), progressive = true)?.let { return it }
        return bestOf(streaming.optJSONArray("adaptiveFormats"), progressive = false)
    }

    private fun bestOf(arr: JSONArray?, progressive: Boolean): Stream? {
        if (arr == null) return null
        var best: Stream? = null
        for (i in 0 until arr.length()) {
            val f = arr.optJSONObject(i) ?: continue
            val mime = f.optString("mimeType")
            if (!mime.startsWith("video/mp4")) continue
            // ciphered streams would need the player JS — skip them, another client may work
            val url = f.optString("url")
            if (url.isEmpty() || f.has("signatureCipher")) continue
            val stream = Stream(
                url = url,
                width = f.optInt("width", 0),
                height = f.optInt("height", 0),
                sizeBytes = f.optLong("contentLength", 0L),
                progressive = progressive,
            )
            if (isBetter(stream, best)) best = stream
        }
        return best
    }

    private fun isBetter(candidate: Stream, current: Stream?): Boolean {
        if (current == null) return true
        fun rank(s: Stream): Int = when {
            s.height in 1..720 -> s.height
            s.height > 720 -> 720 + (s.height - 720) / 8 // gently discourage huge streams
            else -> 0
        }
        if (rank(candidate) != rank(current)) return rank(candidate) > rank(current)
        return candidate.sizeBytes > current.sizeBytes
    }

    private fun postInnertube(body: JSONObject, client: Client): JSONObject {
        val conn = java.net.URL(ENDPOINT).openConnection() as java.net.HttpURLConnection
        return try {
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 15_000
            conn.readTimeout = 20_000
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("User-Agent", client.userAgent)
            conn.setRequestProperty("X-YouTube-Client-Name", client.id.toString())
            conn.setRequestProperty("X-YouTube-Client-Version", client.version)
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            if (conn.responseCode !in 200..299) throw IOException("HTTP ${conn.responseCode} from InnerTube")
            conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }.let(::JSONObject)
        } finally {
            conn.disconnect()
        }
    }
}
