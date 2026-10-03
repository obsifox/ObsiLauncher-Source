package studio.obsifox.obsilauncher.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import studio.obsifox.obsilauncher.R
import studio.obsifox.obsilauncher.app
import studio.obsifox.obsilauncher.core.Paths
import studio.obsifox.obsilauncher.core.ThemeMode
import studio.obsifox.obsilauncher.ui.components.GlassCard
import studio.obsifox.obsilauncher.ui.components.ObsiButton
import studio.obsifox.obsilauncher.ui.components.ObsiGhostButton
import studio.obsifox.obsilauncher.ui.components.ObsiTextButton
import studio.obsifox.obsilauncher.ui.components.ProgressRow
import studio.obsifox.obsilauncher.ui.components.SectionTitle
import studio.obsifox.obsilauncher.ui.theme.LocalObsi
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val app = context.app
    val obsi = LocalObsi.current

    val themeMode by app.settings.themeMode.collectAsState()
    val blur by app.settings.blur.collectAsState()
    val memoryMb by app.settings.memoryMb.collectAsState()
    val javaArgs by app.settings.javaArgs.collectAsState()
    val packs by app.runtimePacks.packs.collectAsState()
    val runtimePack by app.settings.runtimePack.collectAsState()
    val installing by app.runtimePacks.installing.collectAsState()
    val packProgress by app.runtimePacks.progress.collectAsState()
    val customWallpaper by app.settings.customWallpaper.collectAsState()
    val language by app.settings.language.collectAsState()
    val backgroundMode by app.settings.backgroundMode.collectAsState()
    val videoBg by app.wallpaper.videoBg.collectAsState()

    var packUrl by remember { mutableStateOf("") }
    var javaArgsDraft by remember(javaArgs) { mutableStateOf(javaArgs) }
    val scope = rememberCoroutineScope()

    // wallpaper picker (SAF)
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

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
    ) {
        SectionTitle(stringResourceCompat(R.string.settings_background))
        GlassCard {
            Text(
                stringResourceCompat(R.string.settings_bg_mode),
                style = MaterialTheme.typography.titleMedium,
            )
            Row(modifier = Modifier.padding(top = 6.dp)) {
                ObsiGhostButton(
                    text = if (backgroundMode == studio.obsifox.obsilauncher.core.BackgroundMode.WALLPAPER) {
                        "● ${stringResourceCompat(R.string.settings_bg_wallpaper)}"
                    } else {
                        "○ ${stringResourceCompat(R.string.settings_bg_wallpaper)}"
                    },
                    onClick = {
                        app.settings.backgroundModeValue = studio.obsifox.obsilauncher.core.BackgroundMode.WALLPAPER
                        app.wallpaper.sync(app.settings.selectedVersionValue)
                    },
                    enabled = backgroundMode != studio.obsifox.obsilauncher.core.BackgroundMode.WALLPAPER,
                )
            }
            Row(modifier = Modifier.padding(top = 4.dp)) {
                ObsiGhostButton(
                    text = if (backgroundMode == studio.obsifox.obsilauncher.core.BackgroundMode.VIDEO) {
                        "● ${stringResourceCompat(R.string.settings_bg_video)}"
                    } else {
                        "○ ${stringResourceCompat(R.string.settings_bg_video)}"
                    },
                    onClick = {
                        app.settings.backgroundModeValue = studio.obsifox.obsilauncher.core.BackgroundMode.VIDEO
                        app.wallpaper.sync(app.settings.selectedVersionValue)
                    },
                    enabled = backgroundMode != studio.obsifox.obsilauncher.core.BackgroundMode.VIDEO,
                )
            }
            Text(
                stringResourceCompat(R.string.settings_bg_video_note),
                style = MaterialTheme.typography.bodyMedium,
                color = obsi.textDim,
                modifier = Modifier.padding(top = 6.dp),
            )

            Spacer(Modifier.height(8.dp))
            Text(
                stringResourceCompat(R.string.settings_bg_video_status),
                style = MaterialTheme.typography.titleMedium,
            )
            when (val vb = videoBg) {
                is studio.obsifox.obsilauncher.obsi.VideoBg.Downloading -> ProgressRow(
                    label = "${stringResourceCompat(R.string.settings_bg_video_downloading)} " +
                        "${vb.done / 1024 / 1024} / ${if (vb.total > 0) vb.total / 1024 / 1024 else "?"} MB",
                    fraction = if (vb.total > 0) vb.done.toFloat() / vb.total else null,
                    modifier = Modifier.padding(top = 4.dp),
                )
                is studio.obsifox.obsilauncher.obsi.VideoBg.Failed -> {
                    Text(
                        stringResourceCompat(R.string.settings_bg_video_failed, vb.message),
                        style = MaterialTheme.typography.bodyMedium,
                        color = obsi.danger,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    ObsiTextButton(
                        stringResourceCompat(R.string.dl_retry),
                        onClick = { app.wallpaper.ensureVideo() },
                    )
                }
                studio.obsifox.obsilauncher.obsi.VideoBg.Ready -> Text(
                    stringResourceCompat(R.string.settings_bg_video_ready),
                    style = MaterialTheme.typography.bodyMedium,
                    color = obsi.accent,
                    modifier = Modifier.padding(top = 4.dp),
                )
                else -> Text(
                    stringResourceCompat(R.string.settings_bg_video_missing),
                    style = MaterialTheme.typography.bodyMedium,
                    color = obsi.textDim,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            ObsiButton(
                text = stringResourceCompat(R.string.settings_bg_video_download),
                onClick = { app.wallpaper.ensureVideo() },
                enabled = videoBg !is studio.obsifox.obsilauncher.obsi.VideoBg.Downloading,
                modifier = Modifier.padding(top = 8.dp),
            )
            ObsiTextButton(
                text = stringResourceCompat(R.string.settings_bg_resync),
                onClick = { app.wallpaper.sync(app.settings.selectedVersionValue) },
            )
        }

        SectionTitle(stringResourceCompat(R.string.settings_appearance))
        GlassCard {
            val modes = listOf(
                ThemeMode.DYNAMIC to stringResourceCompat(R.string.settings_theme_dynamic),
                ThemeMode.OBSIDIAN to stringResourceCompat(R.string.settings_theme_obsidian),
                ThemeMode.VANILLA to stringResourceCompat(R.string.settings_theme_vanilla),
                ThemeMode.WHITE to stringResourceCompat(R.string.settings_theme_white),
            )
            modes.forEach { (mode, label) ->
                Row(modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)) {
                    ObsiGhostButton(
                        text = if (themeMode == mode) "● $label" else "○ $label",
                        onClick = { app.settings.themeModeValue = mode },
                        enabled = themeMode != mode,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "${stringResourceCompat(R.string.settings_blur)}: $blur",
                style = MaterialTheme.typography.bodyMedium,
                color = obsi.textDim,
            )
            Slider(
                value = blur.toFloat(),
                onValueChange = { app.settings.blurValue = it.toInt() },
                valueRange = 0f..25f,
                steps = 24,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                stringResourceCompat(R.string.settings_custom_wallpaper),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                stringResourceCompat(R.string.settings_custom_wallpaper_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = obsi.textDim,
            )
            Row(modifier = Modifier.padding(top = 8.dp)) {
                ObsiButton(stringResourceCompat(R.string.settings_pick_image), onClick = { pickImage.launch("image/*") })
                ObsiTextButton(
                    stringResourceCompat(R.string.settings_clear_image),
                    onClick = {
                        app.settings.customWallpaperValue = ""
                        Paths.customWallpaper(context).delete()
                        app.wallpaper.sync(app.settings.selectedVersionValue)
                    },
                    enabled = customWallpaper.isNotBlank(),
                )
            }
        }

        SectionTitle(stringResourceCompat(R.string.settings_launcher))
        GlassCard {
            Text(
                "${stringResourceCompat(R.string.settings_memory)}: $memoryMb",
                style = MaterialTheme.typography.bodyMedium,
                color = obsi.textDim,
            )
            Slider(
                value = memoryMb.toFloat(),
                onValueChange = { app.settings.memoryMbValue = ((it.toInt() / 256) * 256).coerceAtLeast(512) },
                valueRange = 512f..8192f,
            )
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = javaArgsDraft,
                onValueChange = { javaArgsDraft = it },
                label = { Text(stringResourceCompat(R.string.settings_java_args)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            ObsiTextButton(stringResourceCompat(R.string.ok), onClick = { app.settings.javaArgsValue = javaArgsDraft })
        }

        SectionTitle(stringResourceCompat(R.string.settings_language))
        GlassCard {
            listOf(
                "system" to stringResourceCompat(R.string.lang_system),
                "en" to stringResourceCompat(R.string.lang_en),
                "fa" to stringResourceCompat(R.string.lang_fa),
            ).forEach { (code, label) ->
                Row(modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)) {
                    ObsiGhostButton(
                        text = if (language == code) "● $label" else "○ $label",
                        onClick = {
                            if (language != code) {
                                app.settings.languageValue = code
                                (context as? android.app.Activity)?.recreate()
                            }
                        },
                        enabled = language != code,
                    )
                }
            }
        }

        SectionTitle(stringResourceCompat(R.string.settings_runtime))
        GlassCard {
            Text(
                stringResourceCompat(R.string.settings_runtime_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = obsi.textDim,
            )
            Spacer(Modifier.height(10.dp))
            if (packs.isEmpty()) {
                Text(stringResourceCompat(R.string.settings_runtime_none), color = obsi.textDim)
            } else {
                packs.forEach { pack ->
                    Row(modifier = Modifier.padding(vertical = 4.dp)) {
                        ObsiGhostButton(
                            text = if (runtimePack == pack.name) "● ${pack.name}" else "○ ${pack.name}",
                            onClick = { app.settings.runtimePackValue = pack.name },
                            enabled = runtimePack != pack.name,
                        )
                        ObsiTextButton(
                            "✕",
                            onClick = {
                                app.runtimePacks.remove(pack.name)
                                if (runtimePack == pack.name) app.settings.runtimePackValue = ""
                            },
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
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
                style = MaterialTheme.typography.bodyMedium,
                color = obsi.textDim,
                modifier = Modifier.padding(top = 10.dp),
            )
        }

        SectionTitle(stringResourceCompat(R.string.nav_about))
        GlassCard {
            Text(stringResourceCompat(R.string.about_free), style = MaterialTheme.typography.bodyMedium)
            Text(stringResourceCompat(R.string.about_credit), style = MaterialTheme.typography.bodyMedium, color = obsi.textDim)
            Text(stringResourceCompat(R.string.about_license), style = MaterialTheme.typography.labelMedium, color = obsi.textDim)
        }
    }
}
