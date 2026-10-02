package studio.obsifox.launcher.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import studio.obsifox.launcher.core.net.MirrorPreset
import studio.obsifox.launcher.core.util.Platform

@Composable
fun SettingsScreen() {
    val app = LocalApp.current
    val st by app.core.settings.flow.collectAsState()
    fun update(f: (studio.obsifox.launcher.core.instance.Settings) -> studio.obsifox.launcher.core.instance.Settings) {
        app.core.settings.update(f)
        app.core.applySettings()
    }
    val o = obsi()

    var jvm by remember { mutableStateOf(st.defaultJvmArgs) }
    var java by remember { mutableStateOf(st.javaPath.orEmpty()) }
    var host by remember { mutableStateOf(st.net.proxyHost.orEmpty()) }
    var port by remember { mutableStateOf(st.net.proxyPort?.toString().orEmpty()) }
    var clientId by remember { mutableStateOf(st.msClientId.orEmpty()) }
    var customWall by remember { mutableStateOf(st.wallpaperCustom.orEmpty()) }
    val sysMb = remember { Platform.totalMemoryMb().takeIf { it > 0 } ?: 16384 }
    val maxSlider = (sysMb * 3 / 4).coerceAtLeast(2048).toFloat()
    val effectiveMax = if (st.defaultMaxMemoryMb > 0) st.defaultMaxMemoryMb else (sysMb / 2).coerceIn(1024, 4096)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionHeader(t("settings_title"))

        // ---------------------------------------------------------------- appearance & wallpaper
        ObsiCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(t("appearance_title"), color = o.text, fontWeight = FontWeight.Bold)
                DropdownField(
                    t("theme"),
                    when (st.themeMode) { "white" -> t("theme_white"); "black" -> t("theme_black"); else -> t("theme_vanilla") },
                    listOf("vanilla" to t("theme_vanilla"), "white" to t("theme_white"), "black" to t("theme_black")),
                    { m -> update { it.copy(themeMode = m) } },
                )
                DropdownField(
                    t("wallpaper_mode"),
                    when (st.wallpaperMode) { "image" -> t("wallpaper_image"); "video" -> t("wallpaper_video"); else -> t("wallpaper_auto") },
                    listOf("auto" to t("wallpaper_auto"), "image" to t("wallpaper_image"), "video" to t("wallpaper_video")),
                    { m -> update { it.copy(wallpaperMode = m) } },
                )
                Dim(t("wallpaper_rule_hint"), maxLines = 3)
                SwitchRow(t("dynamic_tint"), st.dynamicTint, { v -> update { it.copy(dynamicTint = v) } }, sub = t("dynamic_tint_sub"))
                SwitchRow(t("video_sound"), st.wallpaperSound, { v -> update { it.copy(wallpaperSound = v) } }, sub = t("video_sound_sub"))
                ObsiField(
                    customWall,
                    { customWall = it; update { s -> s.copy(wallpaperCustom = it.trim().ifBlank { null }) } },
                    label = t("custom_wallpaper"),
                    placeholder = t("custom_wallpaper_hint"),
                )
            }
        }

        // ---------------------------------------------------------------- language & behaviour
        ObsiCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DropdownField(
                    t("language"),
                    when (st.language) { "en" -> "English"; "fa" -> "فارسی"; else -> t("lang_auto") },
                    listOf("auto" to t("lang_auto"), "en" to "English", "fa" to "فارسی"),
                    { l -> update { it.copy(language = l) } },
                )
                SwitchRow(t("hide_launcher"), st.hideLauncherWhileRunning, { v -> update { it.copy(hideLauncherWhileRunning = v) } })
                SwitchRow(t("open_console"), st.openConsoleOnLaunch, { v -> update { it.copy(openConsoleOnLaunch = v) } })
            }
        }

        ObsiCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${t("memory_default")}: $effectiveMax MB", color = o.text, modifier = Modifier.weight(1f))
                    androidx.compose.material3.Checkbox(st.defaultMaxMemoryMb == 0, { auto -> update { it.copy(defaultMaxMemoryMb = if (auto) 0 else effectiveMax) } })
                    Dim(t("memory_auto"))
                }
                if (st.defaultMaxMemoryMb > 0) {
                    Slider(st.defaultMaxMemoryMb.toFloat(), { v -> update { it.copy(defaultMaxMemoryMb = (v / 256).toInt() * 256) } }, valueRange = 512f..maxSlider,
                        colors = SliderDefaults.colors(thumbColor = o.accent, activeTrackColor = o.accent))
                }
                LabeledField(t("jvm_default"), jvm, { jvm = it; update { s -> s.copy(defaultJvmArgs = it) } }, hint = "-XX:+UseZGC")
                LabeledField(t("java_override"), java, { java = it; update { s -> s.copy(javaPath = it.trim().ifBlank { null }) } }, hint = t("java_auto"))
            }
        }

        ObsiCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("${t("threads")}: ${st.downloadThreads}", color = o.text)
                Slider(st.downloadThreads.toFloat(), { v -> update { it.copy(downloadThreads = v.toInt().coerceIn(2, 32)) } }, valueRange = 2f..32f,
                    colors = SliderDefaults.colors(thumbColor = o.accent, activeTrackColor = o.accent))
                DropdownField(
                    t("mirror"), if (st.net.mirror == MirrorPreset.BMCLAPI) t("mirror_bmcl") else t("mirror_official"),
                    listOf(MirrorPreset.OFFICIAL.name to t("mirror_official"), MirrorPreset.BMCLAPI.name to t("mirror_bmcl")),
                    { m -> update { it.copy(net = it.net.copy(mirror = MirrorPreset.valueOf(m))) } },
                )
                Text(t("proxy"), color = o.text, fontWeight = FontWeight.Medium)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LabeledField(t("proxy_host"), host, { host = it; update { s -> s.copy(net = s.net.copy(proxyHost = it.trim().ifBlank { null })) } }, Modifier.weight(2f), hint = "127.0.0.1")
                    LabeledField(t("proxy_port"), port, { p -> port = p.filter(Char::isDigit).take(5); update { s -> s.copy(net = s.net.copy(proxyPort = port.toIntOrNull())) } }, Modifier.weight(1f), hint = "8080")
                }
            }
        }

        ObsiCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LabeledField(t("ms_client_id"), clientId, { clientId = it; update { s -> s.copy(msClientId = it.trim().ifBlank { null }) } }, hint = t("ms_client_id_hint"))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(t("data_dir"), color = o.text)
                        Dim(app.core.paths.root.toString())
                    }
                    SoftButton(t("open_folder"), { SystemOpen.open(app.core.paths.root) }, icon = ObsiIcons.Folder)
                }
            }
        }
    }
}
