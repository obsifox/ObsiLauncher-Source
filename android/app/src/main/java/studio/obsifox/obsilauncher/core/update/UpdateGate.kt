package studio.obsifox.obsilauncher.core.update

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import studio.obsifox.obsilauncher.BuildConfig
import studio.obsifox.obsilauncher.core.net.Http
import studio.obsifox.obsilauncher.core.runtime.ObsiComponents
import studio.obsifox.obsilauncher.core.runtime.RuntimePacks
import java.io.File

/**
 * The Update Gate — runs automatically on every launch, before the shell:
 *
 *  1. checks GitHub for a newer launcher release (one tap to fetch the APK);
 *  2. checks the requirements for running the game on this device — the
 *     runtime pack / JVM — and installs a default pack automatically when
 *     one is published in the repo's `runtime.json` (no questions asked);
 *  3. hands control to the launcher shell.
 *
 * v1.13.2: failures are never silent any more — every check failure carries
 * its reason in [lastError] / [Step.UpdateCheckFailed], the version check
 * primary source is the quota-free static VERSION asset, and the runtime
 * fallback loop only asks for runtimes that are actually downloadable.
 */
class UpdateGate(private val context: Context, private val runtimePacks: RuntimePacks) {

    sealed class Step {
        data object Idle : Step()
        data object CheckingUpdate : Step()
        data class UpdateAvailable(val tag: String, val notes: String) : Step()
        data class DownloadingUpdate(val done: Long, val total: Long) : Step()
        data object UpdateReady : Step()
        data object UpdateFailed : Step()
        /** the release server could not be reached — shown briefly, then the runtime stage runs */
        data class UpdateCheckFailed(val reason: String) : Step()
        data object CheckingRuntime : Step()
        data class InstallingRuntime(val fraction: Float?) : Step()
        data object RuntimeMissing : Step()
        data object RuntimeFailed : Step()
        data object Ready : Step()
    }

    val step = MutableStateFlow<Step>(Step.Idle)
    val updateApk = MutableStateFlow<File?>(null)

    /** human-readable cause of the last failure (update check, APK download or runtime install) */
    var lastError: String? = null
        private set

    private var pendingTag: String? = null

    private fun repoApiLatest() = "https://api.github.com/repos/obsifox/ObsiLauncher-Source/releases/latest"

    /**
     * Primary version-check source: the VERSION file attached to the latest
     * release. It is a plain static asset — no 60 req/h REST quota, no rate
     * limits on busy days. The REST API is only the fallback.
     */
    private fun versionStaticUrl() =
        "https://github.com/obsifox/ObsiLauncher-Source/releases/latest/download/VERSION"

    /** Full gate run. Always finishes in [Step.Ready], [RuntimeMissing] or [RuntimeFailed]. */
    suspend fun run(skipUpdate: Boolean = false) = withContext(Dispatchers.IO) {
        // ---- 1. launcher update --------------------------------------------
        if (!skipUpdate) {
            step.value = Step.CheckingUpdate
            try {
                val tag = readLatestTag()
                pendingTag = tag
                if (isNewer(tag, BuildConfig.VERSION_NAME)) {
                    step.value = Step.UpdateAvailable(tag, releaseNotes(tag))
                    return@withContext // wait for the user to grab it or continue
                }
            } catch (e: Exception) {
                // offline / throttled: the gate shows WHY and moves on to the runtime
                lastError = e.message ?: e.toString()
                step.value = Step.UpdateCheckFailed(lastError ?: "unknown error")
                return@withContext // UpdateGateScreen shows it ~1.6 s, then calls checkRuntime()
            }
        }

        checkRuntime()
    }

    /**
     * Latest release tag: static VERSION asset first (no quota), REST API as
     * fallback. Throws with the real reason when both fail.
     */
    private suspend fun readLatestTag(): String = withContext(Dispatchers.IO) {
        try {
            Http.get(versionStaticUrl()).trim().removePrefix("v")
        } catch (e: Http.RateLimited) {
            throw e
        } catch (_: Exception) {
            val text = Http.get(repoApiLatest()) // propagates the true failure
            JSONObject(text).optString("tag_name").removePrefix("v")
        }
    }

    /** release name/notes for the banner — best effort, never throws */
    private suspend fun releaseNotes(tag: String): String = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject(Http.get(repoApiLatest()))
            json.optString("name").ifBlank { "ObsiLauncher $tag" }
        } catch (_: Exception) {
            "ObsiLauncher $tag"
        }
    }

    /** ---- 2. runtime / JVM ------------------------------------------------ */
    suspend fun checkRuntime() = withContext(Dispatchers.IO) {
        step.value = Step.CheckingRuntime
        lastError = null

        // v1.13.0 — the gate verifies the SAME store the game boots from:
        // ObsiComponents' Internal-* JREs (the old check validated the separate
        // RuntimePacks store the JVM never reads — the reported "JVM won't
        // open" stemmed from exactly this split).
        runtimePacks.rescan() // keep the advanced pack shelf in sync (settings)

        var bundledCause: String? = null
        ObsiComponents.ensureBundledRuntime(context)
            .onFailure { bundledCause = it.message ?: it.toString() }
        if (bundledCause == null && ObsiComponents.installedRuntimeName(context, 21) != null) {
            step.value = Step.Ready
            return@withContext
        }

        // ---- auto-provision, no questions asked ----------------------------
        // the bundled Internal-21 failed to unpack — try the DOWNLOADABLE
        // catalog runtimes only. jre-21 is not downloadable (it ships in the
        // APK): asking for it used to yield a silent `false` and the user
        // unknowingly landed on Internal-17.
        var lastFailure: Exception? = null
        for (id in DOWNLOADABLE_RUNTIME_IDS) {
            try {
                step.value = Step.InstallingRuntime(null)
                val ok = ObsiComponents.downloadRuntime(context, id, manifestMirrors(id))
                if (ok) {
                    step.value = Step.Ready
                    return@withContext
                }
                lastFailure = IllegalStateException(ObsiComponents.message.value ?: "runtime $id download failed")
            } catch (e: Exception) {
                lastFailure = e
            }
        }
        lastError = lastFailure?.message ?: bundledCause
        step.value = if (lastError != null) Step.RuntimeFailed else Step.RuntimeMissing
    }

    /**
     * Runtime mirror bases for [id], read from the repo's runtime.json (v2).
     * The manifest exists so links can be fixed without an app update — and
     * the list is REALLY handed to the installer now. Returns null when the
     * manifest is unreachable/legacy and the installer should use its
     * built-in pinned mirrors.
     */
    private fun manifestMirrors(id: String): List<String>? {
        return try {
            val text = Http.getOrNull(RUNTIME_MANIFEST_URL) ?: return null
            val root = JSONObject(text)
            if (root.optInt("version", 1) < 2) return null
            val entry = root.optJSONObject("runtimes")?.optJSONObject(id)
            val arr = entry?.optJSONArray("mirrors")
                ?: root.optJSONObject("defaults")?.optJSONArray("mirrors")
                ?: return null
            val urls = mutableListOf<String>()
            for (i in 0 until arr.length()) {
                val u = arr.optString(i)
                if (u.isNotBlank()) urls += u
            }
            urls.ifEmpty { null }
        } catch (_: Exception) {
            null
        }
    }

    /** ---- 3. one-tap self-update ----------------------------------------- */
    suspend fun downloadUpdate() = withContext(Dispatchers.IO) {
        try {
            val tag = pendingTag ?: run {
                val text = Http.get(repoApiLatest())
                JSONObject(text).optString("tag_name").removePrefix("v")
            }
            // resolve the exact asset name via the API when it answers…
            var url: String? = null
            try {
                val assets = JSONObject(Http.get(repoApiLatest())).optJSONArray("assets")
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val a = assets.optJSONObject(i) ?: continue
                        val name = a.optString("name")
                        if (name.endsWith(".apk") && name.contains("arm64")) {
                            url = a.optString("browser_download_url")
                            break
                        }
                    }
                }
            } catch (_: Exception) {
                // …and fall through to the predictable static URL when it doesn't
            }
            // releases/download is a stable pattern: no API quota, no surprise renames
            val apkUrl = url
                ?: "https://github.com/obsifox/ObsiLauncher-Source/releases/download/v$tag/ObsiLauncher-$tag-arm64-v8a.apk"
            val dest = File(context.getExternalFilesDir(null) ?: context.filesDir, "update.apk")
            step.value = Step.DownloadingUpdate(0, 0)
            Http.downloadToFile(apkUrl, dest) { done, total ->
                step.value = Step.DownloadingUpdate(done, total)
            }
            updateApk.value = dest
            step.value = Step.UpdateReady
        } catch (e: Exception) {
            lastError = e.message ?: e.toString()
            step.value = Step.UpdateFailed
        }
    }

    fun reset() {
        step.value = Step.Idle
    }

    companion object {
        /** fetched from the repo so pack URLs can be fixed without an app update */
        const val RUNTIME_MANIFEST_URL =
            "https://raw.githubusercontent.com/obsifox/ObsiLauncher-Source/main/runtime.json"

        /**
         * The gate's auto-provision loop ONLY tries runtimes the installer can
         * actually download. (jre-21 ships inside the APK; requesting it from
         * downloadRuntime used to return a silent false.)
         */
        val DOWNLOADABLE_RUNTIME_IDS = listOf("jre-17", "jre-25")

        /** semantic-ish comparison: 1.10.0 > 1.9.9 */
        fun isNewer(candidate: String, current: String): Boolean {
            fun nums(v: String) = v.removePrefix("v").split('.').map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
            val a = nums(candidate)
            val b = nums(current)
            for (i in 0 until maxOf(a.size, b.size)) {
                val x = a.getOrElse(i) { 0 }
                val y = b.getOrElse(i) { 0 }
                if (x != y) return x > y
            }
            return false
        }
    }
}
