package studio.obsifox.obsilauncher.core.update

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import studio.obsifox.obsilauncher.BuildConfig
import studio.obsifox.obsilauncher.core.net.Http
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
 * Everything runs silently; failures are shown as calm, retryable states.
 */
class UpdateGate(private val context: Context, private val runtimePacks: RuntimePacks) {

    sealed class Step {
        data object Idle : Step()
        data object CheckingUpdate : Step()
        data class UpdateAvailable(val tag: String, val notes: String) : Step()
        data class DownloadingUpdate(val done: Long, val total: Long) : Step()
        data object UpdateReady : Step()
        data object UpdateFailed : Step()
        data object CheckingRuntime : Step()
        data class InstallingRuntime(val fraction: Float?) : Step()
        data object RuntimeMissing : Step()
        data object RuntimeFailed : Step()
        data object Ready : Step()
    }

    val step = MutableStateFlow<Step>(Step.Idle)
    val updateApk = MutableStateFlow<File?>(null)

    private fun repoApiLatest() = "https://api.github.com/repos/obsifox/ObsiLauncher-Source/releases/latest"

    /** Full gate run. Always finishes in [Step.Ready], [RuntimeMissing] or [RuntimeFailed]. */
    suspend fun run(skipUpdate: Boolean = false) = withContext(Dispatchers.IO) {
        // ---- 1. launcher update --------------------------------------------
        if (!skipUpdate) {
            step.value = Step.CheckingUpdate
            try {
                val text = Http.get(repoApiLatest())
                if (text != null) {
                    val json = JSONObject(text)
                    val tag = json.optString("tag_name").removePrefix("v")
                    if (isNewer(tag, BuildConfig.VERSION_NAME)) {
                        step.value = Step.UpdateAvailable(
                            tag,
                            json.optString("name").ifBlank { "ObsiLauncher $tag" },
                        )
                        return@withContext // wait for the user to grab it or continue
                    }
                }
            } catch (_: Exception) {
                // offline: the gate never blocks the launcher for this
            }
        }

        checkRuntime()
    }

    /** ---- 2. runtime / JVM ------------------------------------------------ */
    suspend fun checkRuntime() = withContext(Dispatchers.IO) {
        step.value = Step.CheckingRuntime

        // v1.13.0 — the gate now verifies the SAME store the game boots from:
        // ObsiComponents' Internal-* JREs (the old check validated the separate
        // RuntimePacks store the JVM never reads — the reported "JVM won't
        // open" stemmed from exactly this split).
        runtimePacks.rescan() // keep the advanced pack shelf in sync (settings)
        studio.obsifox.obsilauncher.core.runtime.ObsiComponents.ensureBundledRuntime(context)
        if (studio.obsifox.obsilauncher.core.runtime.ObsiComponents
                .installedRuntimeName(context, 21) != null
        ) {
            step.value = Step.Ready
            return@withContext
        }

        // ---- auto-provision, no questions asked ----------------------------
        // the bundled Internal-21 failed to unpack — try the catalog runtimes
        var lastError: Exception? = null
        for (id in listOf("jre-21", "jre-17", "jre-25")) {
            try {
                step.value = Step.InstallingRuntime(null)
                if (studio.obsifox.obsilauncher.core.runtime.ObsiComponents.downloadRuntime(context, id)) {
                    step.value = Step.Ready
                    return@withContext
                }
            } catch (e: Exception) {
                lastError = e
            }
        }
        step.value = if (lastError != null) Step.RuntimeFailed else Step.RuntimeMissing
    }

    /**
     * Runtime download links, resolved from what the app carries inside:
     *  1. the repo's runtime.json (so links can be fixed without an update)
     *  2. the BUILT-IN catalog below — known open-source Android JRE builds
     *     (PojavLauncher-family, GPL) resolved through the GitHub API.
     */
    private suspend fun resolveRuntimeUrls(): List<String> {
        val urls = mutableListOf<String>()
        runCatching {
            val manifest = JSONObject(Http.get(RUNTIME_MANIFEST_URL) ?: return@runCatching)
            val packs = manifest.optJSONArray("packs") ?: return@runCatching
            for (i in 0 until packs.length()) {
                val p = packs.optJSONObject(i) ?: continue
                val url = p.optString("url")
                if (url.isNotBlank() && p.optString("abi", "arm64-v8a") == "arm64-v8a") urls += url
            }
        }
        if (urls.isEmpty()) urls += builtinRuntimeUrls()
        return urls
    }

    /**
     * The built-in catalog: queries the openjdk-for-android releases feed and
     * returns every arm64 JRE 21/17 asset it publishes, newest first. Falls
     * back to the pinned direct links when the API is unreachable.
     */
    private suspend fun builtinRuntimeUrls(): List<String> = withContext(Dispatchers.IO) {
        val direct = listOf(
            // pinned fallbacks (jre builds published for the PojavLauncher family)
            "https://github.com/PojavLauncherTeam/android-openjdk-build-multiarch/releases/download/jre21-2023/jre21-arm64-2023-10-24.tar.xz",
            "https://github.com/PojavLauncherTeam/android-openjdk-build-multiarch/releases/download/jre17-2023/jre17-arm64-2023-02-04.tar.xz",
        )
        try {
            val text = Http.get(OPENJDK_RELEASES_API) ?: return@withContext direct
            val arr = org.json.JSONArray(text)
            val found = mutableListOf<String>()
            for (i in 0 until arr.length()) {
                val rel = arr.optJSONObject(i) ?: continue
                val assets = rel.optJSONArray("assets") ?: continue
                for (j in 0 until assets.length()) {
                    val a = assets.optJSONObject(j) ?: continue
                    val name = a.optString("name").lowercase()
                    val url = a.optString("browser_download_url")
                    if (url.isBlank()) continue
                    val archOk = name.contains("arm64") || name.contains("aarch64")
                    val jreOk = name.contains("jre") && (name.contains("jre21") || name.contains("21") || name.contains("17"))
                    if (archOk && jreOk && (name.endsWith(".tar.xz") || name.endsWith(".tar.gz"))) found += url
                }
                if (found.size >= 3) break
            }
            found.ifEmpty { direct }
        } catch (_: Exception) {
            direct
        }
    }

    /** ---- 3. one-tap self-update ----------------------------------------- */
    suspend fun downloadUpdate() = withContext(Dispatchers.IO) {
        try {
            val text = Http.get(repoApiLatest()) ?: throw IllegalStateException("no release info")
            val assets = JSONObject(text).optJSONArray("assets")
            var url: String? = null
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
            val apkUrl = url ?: throw IllegalStateException("no arm64 APK in the release")
            val dest = File(context.getExternalFilesDir(null) ?: context.filesDir, "update.apk")
            step.value = Step.DownloadingUpdate(0, 0)
            Http.downloadToFile(apkUrl, dest) { done, total ->
                step.value = Step.DownloadingUpdate(done, total)
            }
            updateApk.value = dest
            step.value = Step.UpdateReady
        } catch (_: Exception) {
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

        /** the open-source Android JRE builds the built-in catalog resolves */
        const val OPENJDK_RELEASES_API =
            "https://api.github.com/repos/PojavLauncherTeam/android-openjdk-build-multiarch/releases?per_page=30"

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
