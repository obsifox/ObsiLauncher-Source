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
        runtimePacks.rescan()
        val valid = runtimePacks.packs.value.any { it.isValid() }
        if (valid) {
            step.value = Step.Ready
            return@withContext
        }

        // auto-provision: the repo publishes default packs in runtime.json
        try {
            val manifest = JSONObject(
                Http.get(RUNTIME_MANIFEST_URL) ?: throw IllegalStateException("no manifest"),
            )
            val packs = manifest.optJSONArray("packs")
            var lastError: Exception? = null
            if (packs != null) {
                for (i in 0 until packs.length()) {
                    val p = packs.optJSONObject(i) ?: continue
                    val url = p.optString("url")
                    val abi = p.optString("abi", "arm64-v8a")
                    if (url.isBlank() || abi != "arm64-v8a") continue
                    try {
                        step.value = Step.InstallingRuntime(null)
                        runtimePacks.install(url, RuntimePacks.nameFor(url))
                        runtimePacks.rescan()
                        if (runtimePacks.packs.value.any { it.isValid() }) {
                            step.value = Step.Ready
                            return@withContext
                        }
                    } catch (e: Exception) {
                        lastError = e
                    }
                }
            }
            step.value = if (lastError != null) Step.RuntimeFailed else Step.RuntimeMissing
        } catch (_: Exception) {
            step.value = Step.RuntimeMissing
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
