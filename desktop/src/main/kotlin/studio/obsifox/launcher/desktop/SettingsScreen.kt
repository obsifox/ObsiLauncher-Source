package studio.obsifox.launcher.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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

    var jvm by remember { mutableStateOf(st.defaultJvmArgs) }
    var java by remember { mutableStateOf(st.javaPath.orEmpty()) }
    var host by remember { mutableStateOf(st.net.proxyHost.orEmpty()) }
    var port by remember { mutableStateOf(st.net.proxyPort?.toString().orEmpty()) }
    val sysMb = remember { Platform.totalMemoryMb().takeIf { it > 0 } ?: 16384 }
    val maxSlider = (sysMb * 3 / 4).coerceAtLeast(2048).toFloat()
    val effectiveMax = if (st.defaultMaxMemoryMb > 0) st.defaultMaxMemoryMb else (sysMb / 2).coerceIn(1024, 4096)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionHeader(t("settings_title"))

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
                Text(t("appearance"), color = Obsi.text, fontWeight = FontWeight.Bold)
                Dim(t("appearance_hint"), maxLines = 2)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ThemePickCard(t("theme_vanilla"), listOf(Color(0xFF321B18), Color(0xFF6D341F), Color(0xFFE06B31)), st.theme == "vanilla", { update { s2 -> s2.copy(theme = "vanilla") } }, Modifier.weight(1f))
                    ThemePickCard(t("theme_white"), listOf(Color(0xFFF3EEE7), Color(0xFFFFAF5), Color(0xFFE2A47E)), st.theme == "white", { update { s2 -> s2.copy(theme = "white") } }, Modifier.weight(1f))
                    ThemePickCard(t("theme_black"), listOf(Color(0xFF070809), Color(0xFF161719), Color(0xFFB55A2D)), st.theme == "black", { update { s2 -> s2.copy(theme = "black") } }, Modifier.weight(1f))
                }
                val pickTitle = t("bg_pick")
                DropdownField(
                    label = t("wiz_step_bg"),
                    selectedLabel = when (st.wallpaperMode) { "latest" -> t("bg_latest"); "custom" -> t("bg_custom"); else -> t("bg_version") },
                    options = listOf("version" to t("bg_version"), "latest" to t("bg_latest"), "custom" to t("bg_custom")),
                    onSelect = { m ->
                        if (m == "custom") {
                            val f = pickImageFile(pickTitle)
                            if (f != null) {
                                app.task(pickTitle) { _ -> app.core.wallpapers.importCustom(f) }
                                update { s2 -> s2.copy(wallpaperMode = "custom") }
                            }
                        } else update { s2 -> s2.copy(wallpaperMode = m) }
                    },
                )
                SwitchRow(t("adapt_colors"), st.adaptColors, { v -> update { s2 -> s2.copy(adaptColors = v) } }, sub = t("adapt_colors_desc"))
                Dim(t("bg_version_old") + "  ·  " + t("video_note"), maxLines = 3)
            }
        }

        ObsiCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${t("memory_default")}: $effectiveMax MB", color = Obsi.text, modifier = Modifier.weight(1f))
                    androidx.compose.material3.Checkbox(st.defaultMaxMemoryMb == 0, { auto -> update { it.copy(defaultMaxMemoryMb = if (auto) 0 else effectiveMax) } })
                    Dim(t("memory_auto"))
                }
                if (st.defaultMaxMemoryMb > 0) {
                    Slider(st.defaultMaxMemoryMb.toFloat(), { v -> update { it.copy(defaultMaxMemoryMb = (v / 256).toInt() * 256) } }, valueRange = 512f..maxSlider,
                        colors = SliderDefaults.colors(thumbColor = Obsi.orange, activeTrackColor = Obsi.orange))
                }
                LabeledField(t("jvm_default"), jvm, { jvm = it; update { s -> s.copy(defaultJvmArgs = it) } }, hint = "-XX:+UseZGC")
                LabeledField(t("java_override"), java, { java = it; update { s -> s.copy(javaPath = it.trim().ifBlank { null }) } }, hint = t("java_auto"))
            }
        }

        ObsiCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("${t("threads")}: ${st.downloadThreads}", color = Obsi.text)
                Slider(st.downloadThreads.toFloat(), { v -> update { it.copy(downloadThreads = v.toInt().coerceIn(2, 32)) } }, valueRange = 2f..32f,
                    colors = SliderDefaults.colors(thumbColor = Obsi.orange, activeTrackColor = Obsi.orange))
                DropdownField(
                    t("mirror"), if (st.net.mirror == MirrorPreset.BMCLAPI) t("mirror_bmcl") else t("mirror_official"),
                    listOf(MirrorPreset.OFFICIAL.name to t("mirror_official"), MirrorPreset.BMCLAPI.name to t("mirror_bmcl")),
                    { m -> update { it.copy(net = it.net.copy(mirror = MirrorPreset.valueOf(m))) } },
                )
                Text(t("proxy"), color = Obsi.text, fontWeight = FontWeight.Medium)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LabeledField(t("proxy_host"), host, { host = it; update { s -> s.copy(net = s.net.copy(proxyHost = it.trim().ifBlank { null })) } }, Modifier.weight(2f), hint = "127.0.0.1")
                    LabeledField(t("proxy_port"), port, { p -> port = p.filter(Char::isDigit).take(5); update { s -> s.copy(net = s.net.copy(proxyPort = port.toIntOrNull())) } }, Modifier.weight(1f), hint = "8080")
                }
            }
        }

        ObsiCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(t("data_dir"), color = Obsi.text)
                        Dim(app.core.paths.root.toString())
                    }
                    SoftButton(t("open_folder"), { SystemOpen.open(app.core.paths.root) }, icon = ObsiIcons.Folder)
                }
            }
        }

        ObsiCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FoxMark(30.dp)
                    Column {
                        Text("ObsiLauncher", color = Obsi.text, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Dim("${t("version")} ${app.build.version} (${app.build.commit})")
                    }
                }
                Dim(t("about_text"), maxLines = 4)
            }
        }
    }
}

@Composable
private fun ThemePickCard(label: String, swatch: List<Color>, selected: Boolean, onPick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(13.dp))
            .border(1.dp, if (selected) Obsi.orange else Obsi.soft, RoundedCornerShape(13.dp))
            .background(if (selected) Obsi.orange.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.03f))
            .clickable(onClick = onPick).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(9.dp)).background(Brush.linearGradient(swatch)))
        Text(label, color = Obsi.text, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}
