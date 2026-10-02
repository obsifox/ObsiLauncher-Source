package studio.obsifox.launcher.core.update

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import studio.obsifox.launcher.core.net.Http
import studio.obsifox.launcher.core.util.RemoteJson
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.UnknownHostException
import java.net.http.HttpConnectTimeoutException
import java.net.http.HttpTimeoutException
import java.nio.channels.UnresolvedAddressException

/** What CI publishes next to the desktop installers (`latest-desktop.json` on the rolling release). */
@Serializable
data class RemoteBuild(val version: String = "", val commit: String = "", val builtAt: String? = null)

sealed interface UpdateStatus {
    /** The published build is the one that is running (or this is a development build with nothing to compare). */
    data class UpToDate(val remote: RemoteBuild? = null) : UpdateStatus

    /** A different build is published. */
    data class Available(val remote: RemoteBuild, val page: String) : UpdateStatus

    /** The update channel could not be reached at all (no internet, DNS failure, timeout). */
    data object Offline : UpdateStatus

    /** The channel answered, but with something unusable (HTTP error, broken manifest). */
    data class Failed(val reason: String) : UpdateStatus
}

/**
 * The launcher's own update check. It reads one small JSON file published by CI, so there are no API rate limits and
 * no tokens. Failure kinds are told apart on purpose: "no internet" is not the same as "the update server is broken".
 */
class UpdateService(private val http: Http, private val url: String = DEFAULT_URL, private val timeoutMs: Long = 7_000) {

    suspend fun check(currentCommit: String): UpdateStatus {
        val body = try {
            withTimeout(timeoutMs) { http.raw("GET", url, mapOf("Accept" to "application/json", "Cache-Control" to "no-cache")) }
        } catch (e: TimeoutCancellationException) {
            return UpdateStatus.Offline
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            return if (isNetworkDown(e)) UpdateStatus.Offline else UpdateStatus.Failed(e.message ?: e.toString())
        }
        if (body.status !in 200..299) return UpdateStatus.Failed("HTTP ${body.status}")
        val remote = try {
            RemoteJson.decodeFromString<RemoteBuild>(body.body)
        } catch (e: Exception) {
            return UpdateStatus.Failed("unreadable update manifest")
        }
        return compare(currentCommit, remote)
    }

    companion object {
        const val DEFAULT_URL = "https://github.com/obsifox/ObsiLauncher-Source/releases/download/nightly/latest-desktop.json"
        const val PAGE = "https://github.com/obsifox/ObsiLauncher-Source/releases/tag/nightly"

        fun compare(currentCommit: String, remote: RemoteBuild): UpdateStatus = when {
            remote.commit.isBlank() -> UpdateStatus.Failed("update manifest has no commit")
            currentCommit.isBlank() || currentCommit == "dev" -> UpdateStatus.UpToDate(remote) // development build: nothing to compare with
            remote.commit.take(7).equals(currentCommit.take(7), ignoreCase = true) -> UpdateStatus.UpToDate(remote)
            else -> UpdateStatus.Available(remote, PAGE)
        }

        fun isNetworkDown(e: Throwable): Boolean {
            var t: Throwable? = e
            var depth = 0
            while (t != null && depth++ < 8) {
                if (t is UnknownHostException || t is ConnectException || t is NoRouteToHostException || t is HttpConnectTimeoutException ||
                    t is HttpTimeoutException || t is UnresolvedAddressException || t is java.net.SocketTimeoutException || t is java.nio.channels.ClosedChannelException
                ) return true
                t = t.cause
            }
            return false
        }
    }
}
