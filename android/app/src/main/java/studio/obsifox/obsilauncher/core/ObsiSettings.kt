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
    fun obsiRoot(ctx: Context): File = File(ctx.filesDir, "obsi")
    fun wallpaperRoot(ctx: Context): File = File(obsiRoot(ctx), "wallpapers")
    fun trailerFile(ctx: Context): File = File(obsiRoot(ctx), "trailer.mp4")
    fun customWallpaper(ctx: Context): File = File(obsiRoot(ctx), "custom-bg")
}

enum class ThemeMode { DYNAMIC, OBSIDIAN, VANILLA, WHITE }

/**
 * The launcher ships two background engines:
 *  - WALLPAPER: per-version official artwork from minecraft.net
 *    (>= 1.12.2 -> Wilderness Bound pack, older -> Minecraft PC bundle)
 *  - VIDEO:    the official trailer as a live background — only for the
 *    latest Minecraft release; other versions fall back to wallpapers
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
    val themeMode = state("theme_mode", ThemeMode.DYNAMIC) { p, k ->
        runCatching { ThemeMode.valueOf(p.getString(k, ThemeMode.DYNAMIC.name) ?: "") }.getOrDefault(ThemeMode.DYNAMIC)
    }
    val blur = state("blur", 14) { p, k -> p.getInt(k, 14).coerceIn(0, 25) }
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

    private companion object {
        fun str(p: SharedPreferences, k: String): String = p.getString(k, "") ?: ""
    }
}
