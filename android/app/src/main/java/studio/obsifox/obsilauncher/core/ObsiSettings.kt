package studio.obsifox.obsilauncher.core

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

/** Storage layout for everything ObsiLauncher manages. */
object Paths {
    fun gamesRoot(ctx: Context): File = File(ctx.filesDir, "games")
    fun versionsRoot(ctx: Context): File = File(gamesRoot(ctx), "versions")
    fun versionDir(ctx: Context, id: String): File = File(versionsRoot(ctx), id)
    fun librariesRoot(ctx: Context): File = File(gamesRoot(ctx), "libraries")
    fun assetsRoot(ctx: Context): File = File(gamesRoot(ctx), "assets")
    fun runtimeRoot(ctx: Context): File = File(ctx.filesDir, "runtime")
    /** unpacked launcher components (lwjgl3 classes, caciocavallo, authlib-injector…) */
    fun componentsRoot(ctx: Context): File = File(ctx.filesDir, "components")
    fun componentDir(ctx: Context, id: String): File = File(componentsRoot(ctx), id)
    fun obsiRoot(ctx: Context): File = File(ctx.filesDir, "obsi")
    fun wallpaperRoot(ctx: Context): File = File(obsiRoot(ctx), "wallpapers")
    fun customWallpaper(ctx: Context): File = File(obsiRoot(ctx), "custom-bg")
    /** game session log (latestlog.txt written by the vendored stdio_is) */
    fun gameLog(ctx: Context): File = File(obsiRoot(ctx), "latestlog.txt")
    /** persisted game-exit marker consumed by the shell after the restart */
    fun exitMarker(ctx: Context): File = File(obsiRoot(ctx), "exit-marker.txt")
    /** runtime root alias used by the JRE component code */
    fun runtimeRootForJre(ctx: Context): File = runtimeRoot(ctx)
}

enum class ThemeMode { MINECRAFT, DYNAMIC, OBSIDIAN, VANILLA, WHITE }

/**
 * The launcher ships two background engines:
 *  - WALLPAPER: per-version official artwork from minecraft.net
 *    (>= 1.12.2 -> Wilderness Bound pack, older -> Minecraft PC bundle)
 *  - VIDEO:    the trailer BUNDLED in the APK as a live background — only for
 *    the latest Minecraft release; after every pass the wallpaper rests on
 *    screen for 45 s and the video starts again (endless cycle). Other
 *    versions fall back to wallpapers.
 */
enum class BackgroundMode { WALLPAPER, VIDEO }

/**
 * Tiny observable settings layer: SharedPreferences on disk,
 * [StateFlow] mirrors for Compose.
 */
class ObsiSettings(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("obsi_settings", Context.MODE_PRIVATE)

    private val listeners = mutableListOf<() -> Unit>()

    init {
        prefs.registerOnSharedPreferenceChangeListener { _, _ ->
            listeners.forEach { it() }
        }
    }

    private fun <T> state(key: String, def: T, read: (SharedPreferences, String) -> T): MutableStateFlow<T> {
        val flow = MutableStateFlow(read(prefs, key))
        listeners += {
            val v = read(prefs, key)
            if (flow.value != v) flow.value = v
        }
        return flow
    }

    // ---- flows (Compose reads these) ---------------------------------------

    val selectedVersion = state("selected_version", "", ::str)
    val themeMode = state("theme_mode", ThemeMode.MINECRAFT) { p, k ->
        runCatching { ThemeMode.valueOf(p.getString(k, ThemeMode.MINECRAFT.name) ?: "") }.getOrDefault(ThemeMode.MINECRAFT)
    }

    // v1.9.0: backgrounds are sharp by default (blur key renamed so existing
    // installs pick up the new clear look)
    val blur = state("blur_clear", 0) { p, k -> p.getInt(k, 0).coerceIn(0, 25) }
    val customWallpaper = state("custom_wallpaper", "", ::str)
    val memoryMb = state("memory_mb", 2048) { p, k -> p.getInt(k, 2048).coerceIn(512, 8192) }
    val javaArgs = state("java_args", "", ::str)
    val runtimePack = state("runtime_pack", "", ::str)
    val language = state("language", "system") { p, k -> p.getString(k, "system") ?: "system" }
    val backgroundMode = state("background_mode", BackgroundMode.WALLPAPER) { p, k ->
        runCatching { BackgroundMode.valueOf(p.getString(k, BackgroundMode.WALLPAPER.name) ?: "") }
            .getOrDefault(BackgroundMode.WALLPAPER)
    }
    val setupDone = state("setup_done", false) { p, k -> p.getBoolean(k, false) }

    // v1.10.0: the bundled background video has sound by default; the user
    // can mute it with the on-screen speaker button (or here in settings)
    val videoMuted = state("video_muted", false) { p, k -> p.getBoolean(k, false) }

    // ---- typed accessors -----------------------------------------------------

    var selectedVersionValue: String
        get() = selectedVersion.value
        set(v) { prefs.edit().putString("selected_version", v).apply() }

    var themeModeValue: ThemeMode
        get() = themeMode.value
        set(v) { prefs.edit().putString("theme_mode", v.name).apply() }

    var blurValue: Int
        get() = blur.value
        set(v) { prefs.edit().putInt("blur", v).apply() }

    var customWallpaperValue: String
        get() = customWallpaper.value
        set(v) { prefs.edit().putString("custom_wallpaper", v).apply() }

    var memoryMbValue: Int
        get() = memoryMb.value
        set(v) { prefs.edit().putInt("memory_mb", v).apply() }

    var javaArgsValue: String
        get() = javaArgs.value
        set(v) { prefs.edit().putString("java_args", v).apply() }

    var runtimePackValue: String
        get() = runtimePack.value
        set(v) { prefs.edit().putString("runtime_pack", v).apply() }

    var languageValue: String
        get() = language.value
        set(v) { prefs.edit().putString("language", v).apply() }

    var backgroundModeValue: BackgroundMode
        get() = backgroundMode.value
        set(v) { prefs.edit().putString("background_mode", v.name).apply() }

    var setupDoneValue: Boolean
        get() = setupDone.value
        set(v) { prefs.edit().putBoolean("setup_done", v).apply() }

    var videoMutedValue: Boolean
        get() = videoMuted.value
        set(v) { prefs.edit().putBoolean("video_muted", v).apply() }

    private companion object {
        fun str(p: SharedPreferences, k: String): String = p.getString(k, "") ?: ""
    }
}
