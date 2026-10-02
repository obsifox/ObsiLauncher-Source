package studio.obsifox.launcher.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import studio.obsifox.launcher.core.instance.Instance
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault())

@Composable
fun lastPlayedText(i: Instance): String =
    if (i.lastPlayedAt <= 0) t("never_played") else tf("last_played", dateFmt.format(Instant.ofEpochMilli(i.lastPlayedAt)))

@Composable
fun InstanceIcon(i: Instance, size: androidx.compose.ui.unit.Dp = 56.dp) {
    RemoteImage(i.iconUrl, i.name, size)
}

/**
 * The immersive home: full-screen artwork of the selected profile's Minecraft version, navigation on top,
 * session facts at the bottom start edge and the launch action at the bottom end edge (mirrored in RTL).
 */
@Composable
fun HomeScreen() {
    val app = LocalApp.current
    val instances by app.core.instances.flow.collectAsState()
    val settings by app.core.settings.flow.collectAsState()
    val accounts by app.core.accounts.flow.collectAsState()
    val sessions by app.sessions.collectAsState()
    val launching by app.launchingIds.collectAsState()
    val offline by app.offlineMode.collectAsState()
    val backdrop by app.backdrop.collectAsState()

    val selected = instances.firstOrNull { it.id == settings.selectedInstanceId } ?: instances.firstOrNull()
    val account = accounts.firstOrNull { it.id == settings.selectedAccountId } ?: accounts.firstOrNull()
    val running = selected != null && sessions[selected.id]?.running?.value == true
    val preparing = selected != null && selected.id in launching

    Box(Modifier.fillMaxSize()) {
        WallpaperLayer(app)
        Column(Modifier.fillMaxSize().padding(22.dp)) {
            HomeTopBar(app, account, selected)
            Box(Modifier.weight(1f)) {
                if (selected == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        GlassPanel(strong = true, radius = 22.dp) {
                            Column(Modifier.padding(28.dp), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(t("no_instance_title"), style = MaterialTheme.typography.titleLarge, color = Obsi.text)
                                Dim(t("no_instance_text"), maxLines = 2)
                                PrimaryButton(t("create_instance"), { app.go(Screen.Instances) }, icon = ObsiIcons.Add)
                            }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                StatsPanel(selected)
                Box(Modifier.weight(1f))
                LaunchZone(app, selected, running, preparing, offline, backdrop.loading)
            }
            TaskBar(app)
        }
    }
}

@Composable
private fun HomeTopBar(app: AppController, account: studio.obsifox.launcher.core.auth.Account?, selected: Instance?) {
    val instances by app.core.instances.flow.collectAsState()
    val accounts by app.core.accounts.flow.collectAsState()
    var profilesOpen by remember { mutableStateOf(false) }
    var versionsOpen by remember { mutableStateOf(false) }

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        GlassPanel(Modifier.weight(1f, fill = false)) {
            Row(Modifier.padding(6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                // profile first
                Box {
                    Row(
                        Modifier.padding(4.dp).clip(RoundedCornerShape(11.dp)).clickable { profilesOpen = true }.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        LetterAvatar(account?.username ?: "?", 34.dp, shape = RoundedCornerShape(9.dp))
                        Column {
                            Text(account?.username ?: t("no_account"), color = Obsi.text, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(t("account_offline"), color = Obsi.textDim, fontSize = 8.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
                        }
                        Icon(ObsiIcons.ArrowDown, null, tint = Obsi.textDim, modifier = Modifier.size(13.dp))
                    }
                    DropdownMenu(expanded = profilesOpen, onDismissRequest = { profilesOpen = false }) {
                        accounts.forEach { a -> DropdownMenuItem(text = { Text(a.username) }, onClick = { app.selectAccount(a.id); profilesOpen = false }) }
                        DropdownMenuItem(text = { Text(t("add_offline")) }, onClick = { profilesOpen = false; app.go(Screen.Accounts) })
                    }
                }
                Sep()
                NavIconButton(ObsiIcons.Play, t("play"), enabled = selected != null) { selected?.let(app::play) }
                // version selector = profile selector, the wallpaper follows it
                Box {
                    Row(
                        Modifier.padding(4.dp).clip(RoundedCornerShape(11.dp)).clickable { versionsOpen = true }.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(ObsiIcons.Cube, null, tint = Obsi.accentLight, modifier = Modifier.size(15.dp))
                        Text(selected?.subtitle ?: "—", color = Obsi.text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        Icon(ObsiIcons.ArrowDown, null, tint = Obsi.textDim, modifier = Modifier.size(12.dp))
                    }
                    DropdownMenu(expanded = versionsOpen, onDismissRequest = { versionsOpen = false }) {
                        instances.forEach { i ->
                            DropdownMenuItem(text = { Column { Text(i.name); Text(i.subtitle, color = Obsi.textDim, fontSize = 10.sp) } },
                                onClick = { app.selectInstance(i.id); versionsOpen = false })
                        }
                        DropdownMenuItem(text = { Text(t("create_instance")) }, onClick = { versionsOpen = false; app.go(Screen.Instances) })
                    }
                }
                NavIconButton(ObsiIcons.Extension, t("nav_instances")) { app.go(selected?.let { Screen.InstanceDetail(it.id, 0) } ?: Screen.Instances) }
                NavIconButton(ObsiIcons.Settings, t("nav_settings")) { app.go(Screen.Settings) }
            }
        }
        GlassPanel {
            Row(Modifier.padding(6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                LanguageSwitch()
                NavIconButton(ObsiIcons.Fullscreen, t("fs_toggle")) { app.fullscreen.value = !app.fullscreen.value }
                Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    FoxMark(24.dp)
                    Text("ObsiLauncher", color = Obsi.textDim, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
            }
        }
    }
}



@Composable
private fun Sep() = Box(Modifier.width(1.dp).height(24.dp).background(Obsi.soft))

@Composable
private fun NavIconButton(icon: androidx.compose.ui.graphics.vector.ImageVector, tip: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).clickable(onClick = onClick, enabled = enabled), contentAlignment = Alignment.Center) {
        Icon(icon, tip, tint = Obsi.accentLight, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun StatsPanel(selected: Instance?) {
    val strings = LocalStrings.current
    GlassPanel {
        Row(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(26.dp), verticalAlignment = Alignment.CenterVertically) {
            Stat(ObsiIcons.Cube, t("stat_install"), selected?.subtitle ?: "—")
            Stat(ObsiIcons.Clock, t("stat_playtime"), if ((selected?.playTimeSeconds ?: 0) > 0) strings.duration(selected!!.playTimeSeconds) else "—")
            Stat(ObsiIcons.Calendar, t("stat_last"), selected?.let { lastPlayedText(it) } ?: "—")
        }
    }
}

@Composable
private fun Stat(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(icon, null, tint = Obsi.accentLight, modifier = Modifier.size(24.dp))
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(label, color = Obsi.textDim, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
            Text(value, color = Obsi.text, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun LaunchZone(app: AppController, selected: Instance?, running: Boolean, preparing: Boolean, offline: Boolean, artLoading: Boolean) {
    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(9.dp)) {
        if (artLoading) Dim(t("art_loading"), size = 10)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            val led = when { running -> Obsi.green; preparing -> Obsi.orange; offline -> Color(0xFFC4A06E); else -> Color(0xFFB8D89A) }
            Box(Modifier.size(6.dp).background(led, CircleShape))
            Text(
                when {
                    offline -> t("offline_status")
                    preparing -> t("loading")
                    else -> t("ready_status")
                },
                color = Obsi.text.copy(alpha = 0.8f), fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp,
            )
        }
        if (running && selected != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SoftButton(t("stop"), { app.stop(selected.id) }, icon = ObsiIcons.Stop, danger = true)
                PrimaryButton(t("nav_console"), { app.go(Screen.Console) }, icon = ObsiIcons.Terminal)
            }
        } else {
            PlayButton(enabled = selected != null && !preparing) { selected?.let(app::play) }
        }
    }
}

@Composable
private fun PlayButton(enabled: Boolean, onClick: () -> Unit) {
    val selected by LocalApp.current.core.instances.flow.collectAsState()
    val settings by LocalApp.current.core.settings.flow.collectAsState()
    val inst = selected.firstOrNull { it.id == settings.selectedInstanceId } ?: selected.firstOrNull()
    Box(
        Modifier.width(330.dp).height(104.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(Obsi.orange, Obsi.orangeDeep)))
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(17.dp)).background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.10f), Color.Transparent))), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(t("play"), color = Obsi.onAccent, fontSize = 34.sp, fontWeight = FontWeight.Black, letterSpacing = 6.sp)
                Text(inst?.subtitle ?: "", color = Obsi.onAccent.copy(alpha = 0.85f), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
