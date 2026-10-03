package studio.obsifox.obsilauncher.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import studio.obsifox.obsilauncher.R
import studio.obsifox.obsilauncher.app
import studio.obsifox.obsilauncher.core.Paths
import studio.obsifox.obsilauncher.core.ThemeMode
import studio.obsifox.obsilauncher.ui.components.ObsiButton
import studio.obsifox.obsilauncher.ui.components.ObsiTextButton
import studio.obsifox.obsilauncher.ui.components.ProgressRow
import studio.obsifox.obsilauncher.ui.theme.LocalObsi

private val SettingsTabs = listOf("general", "game", "components", "look")

/**
 * Settings as the reference design: a left rail of section pills and one
 * glass panel of label-versus-control rows on the right.
 */
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val app = context.app
    val obsi = LocalObsi.current

    var tab by remember { mutableIntStateOf(0) }

    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp)) {
        // left rail ------------------------------------------------------------
        Column(
            Modifier
                .width(150.dp)
                .fillMaxHeight()
                .padding(top = 4.dp, end = 12.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(7.dp),
        ) {
            SettingsTabs.forEachIndexed { i, key ->
                val selected = tab == i
                Text(
                    text = stringResourceCompat(
                        when (key) {
                            "general" -> R.string.set_tab_general
                            "game" -> R.string.set_tab_game
                            "components" -> R.string.set_tab_components
                            else -> R.string.set_tab_look
                        },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = if (selected) obsi.accent else obsi.text,
                    maxLines = 1,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selected) Color(0x2EF26A1B) else Color(0x2E140F0C))
                        .border(
                            1.dp,
                            if (selected) obsi.accent.copy(alpha = 0.6f) else Color(0x24FFFFFF),
                            RoundedCornerShape(12.dp),
                        )
                        .clickable { tab = i }
                        .padding(horizontal = 13.dp, vertical = 11.dp),
                )
            }
        }

        // right panel ------------------------------------------------------------
        Column(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0x4D140F0C))
                .border(1.dp, Color(0x24FFFFFF), RoundedCornerShape(16.dp))
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            when (tab) {
                0 -> GeneralPanel()
                1 -> GamePanel()
                2 -> ComponentsPanel()
                else -> LookPanel()
            }
        }
    }
}

@Composable
private fun SettingRow(label: String, hint: String? = null, content: @Composable () -> Unit = {}) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 9.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = Color(0xFFF4EFEA))
            if (hint != null) {
                Text(hint, style = MaterialTheme.typography.labelMedium, color = LocalObsi.current.textDim)
            }
        }
        Spacer(Modifier.width(12.dp))
        content()
    }
}

@Composable
private fun PanelDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .height(1.dp)
            .background(Color(0x1AFFFFFF)),
    )
}

@Composable
private fun PanelTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = LocalObsi.current.accent,
        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
    )
}

// ---------------------------------------------------------------- general

@Composable
private fun GeneralPanel() {
    val context = LocalContext.current
    val app = context.app
    val obsi = LocalObsi.current
    val language by app.settings.language.collectAsState()

    PanelTitle(stringResourceCompat(R.string.settings_language))
    SettingRow(stringResourceCompat(R.string.settings_language)) {
        Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) {
            listOf(
                "system" to stringResourceCompat(R.string.lang_system),
                "en" to stringResourceCompat(R.string.lang_en),
                "fa" to stringResourceCompat(R.string.lang_fa),
            ).forEach { (code, label) ->
                val selected = language == code
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = if (selected) Color.White else obsi.textDim,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selected) obsi.accent else Color(0x2AFFFFFF))
                        .clickable {
                            // v1.13.0 — NO Activity.recreate() here: the
                            // ObsiLanguage wrapper re-resolves every string
                            // live the moment the setting flips
                            if (language != code) {
                                app.settings.languageValue = code
                            }
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
    }
    PanelDivider()
    PanelTitle(stringResourceCompat(R.string.settings_background))
    BackgroundRows()
    PanelDivider()
    SettingRow(stringResourceCompat(R.string.about_free), hint = stringResourceCompat(R.string.about_credit))
}

@Composable
private fun BackgroundRows() {
    val context = LocalContext.current
    val app = context.app
    val obsi = LocalObsi.current
    val backgroundMode by app.settings.backgroundMode.collectAsState()
    val videoMuted by app.settings.videoMuted.collectAsState()

    SettingRow(stringResourceCompat(R.string.settings_bg_mode)) {
        Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) {
            listOf(
                studio.obsifox.obsilauncher.core.BackgroundMode.WALLPAPER to stringResourceCompat(R.string.settings_bg_wallpaper),
                studio.obsifox.obsilauncher.core.BackgroundMode.VIDEO to stringResourceCompat(R.string.settings_bg_video),
            ).forEach { (mode, label) ->
                val selected = backgroundMode == mode
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = if (selected) Color.White else obsi.textDim,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selected) obsi.accent else Color(0x2AFFFFFF))
                        .clickable {
                            app.settings.backgroundModeValue = mode
                            app.wallpaper.sync(app.settings.selectedVersionValue)
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
    }

    // v1.10.0: the trailer is bundled in the APK — no download state anymore
    SettingRow(
        stringResourceCompat(R.string.settings_bg_video_status),
        hint = stringResourceCompat(R.string.settings_bg_video_bundled),
    ) {}
    SettingRow(stringResourceCompat(R.string.settings_video_sound)) {
        Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) {
            listOf(
                false to stringResourceCompat(R.string.settings_video_sound_on),
                true to stringResourceCompat(R.string.settings_video_sound_off),
            ).forEach { (muted, label) ->
                val selected = videoMuted == muted
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = if (selected) Color.White else obsi.textDim,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selected) obsi.accent else Color(0x2AFFFFFF))
                        .clickable { app.settings.videoMutedValue = muted }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
    }
}

// -------------------------------------------------------------------- game

@Composable
private fun GamePanel() {
    val context = LocalContext.current
    val app = context.app
    val obsi = LocalObsi.current

    val memoryMb by app.settings.memoryMb.collectAsState()
    val javaArgs by app.settings.javaArgs.collectAsState()
    var javaArgsDraft by remember(javaArgs) { mutableStateOf(javaArgs) }

    val packs by app.runtimePacks.packs.collectAsState()
    val runtimePack by app.settings.runtimePack.collectAsState()
    val installing by app.runtimePacks.installing.collectAsState()
    val packProgress by app.runtimePacks.progress.collectAsState()
    var packUrl by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    PanelTitle(stringResourceCompat(R.string.settings_memory))
    SettingRow(
        "${stringResourceCompat(R.string.settings_memory)}: $memoryMb MB",
        hint = stringResourceCompat(R.string.settings_ram_hint),
    ) {}
    Slider(
        value = memoryMb.toFloat(),
        onValueChange = { app.settings.memoryMbValue = ((it.toInt() / 256) * 256).coerceAtLeast(512) },
        valueRange = 512f..8192f,
    )
    PanelDivider()
    PanelTitle(stringResourceCompat(R.string.settings_java_args))
    OutlinedTextField(
        value = javaArgsDraft,
        onValueChange = { javaArgsDraft = it },
        label = { Text(stringResourceCompat(R.string.settings_java_args)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    ObsiTextButton(stringResourceCompat(R.string.ok), onClick = { app.settings.javaArgsValue = javaArgsDraft })
    PanelDivider()
    PanelTitle(stringResourceCompat(R.string.settings_runtime))

    if (packs.isEmpty()) {
        Text(stringResourceCompat(R.string.settings_runtime_none), color = obsi.textDim, style = MaterialTheme.typography.bodyMedium)
    } else {
        packs.forEach { pack ->
            SettingRow(pack.name) {
                Row {
                    if (runtimePack != pack.name) {
                        ObsiTextButton(stringResourceCompat(R.string.common_select), onClick = {
                            app.settings.runtimePackValue = pack.name
                        })
                    } else {
                        Text(
                            "●",
                            color = obsi.accent,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(horizontal = 8.dp),
                        )
                    }
                    ObsiTextButton(
                        "✕",
                        onClick = {
                            app.runtimePacks.remove(pack.name)
                            if (runtimePack == pack.name) app.settings.runtimePackValue = ""
                        },
                    )
                }
            }
            PanelDivider()
        }
    }
    OutlinedTextField(
        value = packUrl,
        onValueChange = { packUrl = it },
        label = { Text(stringResourceCompat(R.string.settings_runtime_url)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    if (installing) {
        ProgressRow(stringResourceCompat(R.string.settings_runtime_install), packProgress, Modifier.padding(top = 8.dp))
    } else {
        ObsiButton(
            text = stringResourceCompat(R.string.settings_runtime_install),
            onClick = {
                val url = packUrl.trim()
                if (url.isNotEmpty()) {
                    scope.launch {
                        try {
                            app.runtimePacks.install(url, studio.obsifox.obsilauncher.core.runtime.RuntimePacks.nameFor(url))
                            app.settings.runtimePackValue = studio.obsifox.obsilauncher.core.runtime.RuntimePacks.nameFor(url)
                        } catch (_: Exception) {
                        }
                    }
                }
            },
            enabled = packUrl.isNotBlank(),
            modifier = Modifier.padding(top = 8.dp),
        )
    }
    Text(
        stringResourceCompat(R.string.settings_runtime_docs),
        style = MaterialTheme.typography.labelMedium,
        color = obsi.textDim,
        modifier = Modifier.padding(top = 8.dp, bottom = 6.dp),
    )
}

// -------------------------------------------------------------------- look

@Composable
private fun LookPanel() {
    val context = LocalContext.current
    val app = context.app
    val obsi = LocalObsi.current

    val themeMode by app.settings.themeMode.collectAsState()
    val blur by app.settings.blur.collectAsState()
    val customWallpaper by app.settings.customWallpaper.collectAsState()

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            val dest = Paths.customWallpaper(context)
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    dest.outputStream().use { input.copyTo(it) }
                }
                app.settings.customWallpaperValue = dest.absolutePath
                app.wallpaper.sync(app.settings.selectedVersionValue)
            }
        }
    }

    PanelTitle(stringResourceCompat(R.string.settings_appearance))
    val modes = listOf(
        ThemeMode.MINECRAFT to stringResourceCompat(R.string.settings_theme_minecraft),
        ThemeMode.DYNAMIC to stringResourceCompat(R.string.settings_theme_dynamic),
        ThemeMode.OBSIDIAN to stringResourceCompat(R.string.settings_theme_obsidian),
        ThemeMode.VANILLA to stringResourceCompat(R.string.settings_theme_vanilla),
        ThemeMode.WHITE to stringResourceCompat(R.string.settings_theme_white),
    )
    SettingRow(stringResourceCompat(R.string.settings_appearance)) {
        Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) {
            modes.forEach { (mode, label) ->
                val selected = themeMode == mode
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = if (selected) Color.White else obsi.textDim,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selected) obsi.accent else Color(0x2AFFFFFF))
                        .clickable { app.settings.themeModeValue = mode }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
    }
    PanelDivider()
    SettingRow("${stringResourceCompat(R.string.settings_blur)}: $blur") {}
    Slider(
        value = blur.toFloat(),
        onValueChange = { app.settings.blurValue = it.toInt() },
        valueRange = 0f..25f,
        steps = 24,
    )
    PanelDivider()
    PanelTitle(stringResourceCompat(R.string.settings_custom_wallpaper))
    SettingRow(
        stringResourceCompat(R.string.settings_custom_wallpaper),
        hint = stringResourceCompat(R.string.settings_custom_wallpaper_hint),
    ) {
        Row {
            ObsiButton(stringResourceCompat(R.string.settings_pick_image), onClick = { pickImage.launch("image/*") })
            ObsiTextButton(
                stringResourceCompat(R.string.settings_clear_image),
                onClick = {
                    app.settings.customWallpaperValue = ""
                    Paths.customWallpaper(context).delete()
                    app.wallpaper.sync(app.settings.selectedVersionValue)
                },
                enabled = customWallpaper.isNotBlank(),
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

// ----------------------------------------------------------------- components

/**
 * v1.13.0 — the FULL component/runtime shelf, as briefed: every runtime and
 * file the launcher can use, visible and downloadable in one place.
 *
 *   Internal-8/17/21/25  the Java runtimes (MC ≤1.16 / 1.17+ / ≥1.20.5 / 25)
 *   authlib-injector     external-auth injection runtime
 *   caciocavallo (+17)   portable OpenJDK AWT backend (Java 8 / Java 17+)
 *   JNA                  low-level system-call bridge (libjnidispatch)
 *   Launcher Components  the launcher's own toolkit (Mio patcher, log4j…)
 *   LWJGL 3              the graphics/OpenGL/input/audio fork (3.3.6 covers
 *                        the old 3.3.3 and the newer 3.4.x requirements)
 *
 * Downloads unpack immediately and are picked up the NEXT time the game
 * starts — the running JVM (or the next one) reads them at boot.
 */
@Composable
private fun ComponentsPanel() {
    val context = LocalContext.current
    val app = context.app
    val obsi = LocalObsi.current
    val scope = rememberCoroutineScope()

    var tick by remember { mutableIntStateOf(0) }
    var busyName by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableStateOf<Float?>(null) }

    // live progress of ObsiComponents installs (gate / pre-flight share them)
    val installing by studio.obsifox.obsilauncher.core.runtime.ObsiComponents.installing.collectAsState()
    val installProgress by studio.obsifox.obsilauncher.core.runtime.ObsiComponents.progress.collectAsState()

    val statuses = remember(tick, installing) {
        studio.obsifox.obsilauncher.core.runtime.ObsiComponents.statuses(context)
    }

    PanelTitle(stringResourceCompat(R.string.set_tab_components))
    Text(
        stringResourceCompat(R.string.components_hint),
        style = MaterialTheme.typography.labelMedium,
        color = obsi.textDim,
        modifier = Modifier.padding(bottom = 6.dp),
    )

    statuses.forEach { st ->
        val c = st.component
        SettingRow(
            label = c.displayName,
            hint = when {
                st.busy -> stringResourceCompat(R.string.components_downloading)
                st.installed -> stringResourceCompat(R.string.components_installed)
                c.isRuntime -> stringResourceCompat(R.string.components_runtime_missing)
                else -> null
            },
        ) {
            Row {
                when {
                    st.busy || (installing != null && installing == c.displayName) -> {
                        Text(
                            "…",
                            style = MaterialTheme.typography.titleMedium,
                            color = obsi.accent,
                            modifier = Modifier.padding(horizontal = 8.dp),
                        )
                    }
                    st.installed -> {
                        Text(
                            "●",
                            color = Color(0xFF7ED957),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(horizontal = 8.dp),
                        )
                        if (!c.bundled || c.isRuntime) {
                            ObsiTextButton(stringResourceCompat(R.string.components_delete), onClick = {
                                studio.obsifox.obsilauncher.core.runtime.ObsiComponents.remove(context, c.id)
                                tick++
                            })
                        }
                    }
                    else -> {
                        ObsiTextButton(stringResourceCompat(R.string.components_download), onClick = {
                            scope.launch {
                                busyName = c.displayName
                                progress = null
                                if (c.isRuntime) {
                                    studio.obsifox.obsilauncher.core.runtime.ObsiComponents.downloadRuntime(context, c.id)
                                } else {
                                    // bundled-but-damaged component: re-unpack
                                    studio.obsifox.obsilauncher.core.runtime.ObsiComponents.unpackBundled(context, c.id)
                                }
                                busyName = null
                                tick++
                            }
                        })
                    }
                }
            }
        }
        if (installing != null && installing == c.displayName && installProgress != null) {
            ProgressRow(stringResourceCompat(R.string.components_downloading), installProgress)
        }
        PanelDivider()
    }

    // the manual RuntimePacks url-install stays available at the bottom
    Text(
        stringResourceCompat(R.string.components_pack_hint),
        style = MaterialTheme.typography.labelMedium,
        color = obsi.textDim,
        modifier = Modifier.padding(top = 8.dp, bottom = 6.dp),
    )
    var packUrl by remember { mutableStateOf("") }
    OutlinedTextField(
        value = packUrl,
        onValueChange = { packUrl = it },
        label = { Text(stringResourceCompat(R.string.settings_runtime_url)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    ObsiButton(
        text = stringResourceCompat(R.string.settings_runtime_install),
        onClick = {
            val url = packUrl.trim()
            if (url.isNotEmpty()) {
                scope.launch {
                    try {
                        app.runtimePacks.install(url, studio.obsifox.obsilauncher.core.runtime.RuntimePacks.nameFor(url))
                        tick++
                    } catch (_: Exception) {
                    }
                }
            }
        },
        enabled = packUrl.isNotBlank(),
        modifier = Modifier.padding(top = 8.dp),
    )
}
