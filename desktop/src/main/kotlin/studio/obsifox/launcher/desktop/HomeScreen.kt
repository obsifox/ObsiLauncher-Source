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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import studio.obsifox.launcher.core.auth.Account
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
 * Home = the wallpaper itself is the screen (spec: "keep the cinematic scene open").
 * Top: account + live status. Bottom-left: session strip. Bottom-right: big Play.
 */
@Composable
fun HomeScreen() {
    val app = LocalApp.current
    val instances by app.core.instances.flow.collectAsState()
    val settings by app.core.settings.flow.collectAsState()
    val accounts by app.core.accounts.flow.collectAsState()
    val sessions by app.sessions.collectAsState()
    val strings by app.strings.collectAsState()
    val o = obsi()

    val selected = instances.firstOrNull { it.id == settings.selectedInstanceId } ?: instances.firstOrNull()
    val account = accounts.firstOrNull { it.id == settings.selectedAccountId } ?: accounts.firstOrNull()
    val packLabel = LocalWallpaperPackLabel.current

    Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // ---------------------------------------------------------------- top row: account + background label
        Row(verticalAlignment = Alignment.CenterVertically) {
            AccountChip(account, accounts, app)
            Box(Modifier.weight(1f))
            Pill(packLabel.ifEmpty { "Wilderness Bound" }.let { if (settings.wallpaperMode == "video") "$it · VIDEO" else it }, o.accent)
        }

        // ---------------------------------------------------------------- hero (center-left)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            if (selected == null) {
                ObsiCard(Modifier.width(560.dp), color = o.glassHigh) {
                    Column(Modifier.padding(30.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FoxMark(34.dp, tint = o.accent)
                            HSpace(12)
                            Column {
                                Text(t("home_title"), style = MaterialTheme.typography.headlineSmall, color = o.text)
                                Dim(t("home_sub"))
                            }
                        }
                        EmptyState(ObsiIcons.Apps, t("no_instance_title"), t("no_instance_text")) {
                            PrimaryButton(t("create_instance"), { app.go(Screen.Instances) }, icon = ObsiIcons.Add)
                        }
                    }
                }
            } else {
                val running = sessions[selected.id]?.running?.value == true
                ObsiCard(Modifier.width(600.dp), color = o.glassHigh) {
                    Column(Modifier.padding(26.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                            InstanceIcon(selected, 76.dp)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(selected.name, style = MaterialTheme.typography.titleLarge, color = o.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(selected.subtitle, color = o.accent, fontWeight = FontWeight.Medium)
                                Dim(lastPlayedText(selected) + if (selected.playTimeSeconds > 0) "  ·  " + tf("play_time", strings.duration(selected.playTimeSeconds)) else "")
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (running) {
                                SoftButton(t("stop"), { app.stop(selected.id) }, icon = ObsiIcons.Stop, danger = true)
                                PrimaryButton(t("nav_console"), { app.go(Screen.Console) }, icon = ObsiIcons.Terminal)
                            } else {
                                PrimaryButton(t("play"), { app.play(selected) }, icon = ObsiIcons.Play)
                                SoftButton(t("nav_instances"), { app.go(Screen.Instances) }, icon = ObsiIcons.Apps)
                            }
                        }
                    }
                }
            }
        }

        // ---------------------------------------------------------------- bottom: session strip + play
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            ObsiCard(Modifier.weight(1f).height(58.dp), color = o.glassLow) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Box(Modifier.size(9.dp).clip(RoundedCornerShape(50)).background(if (sessions.isNotEmpty()) o.good else o.accent))
                    Text(
                        selected?.let { "${it.subtitle}  ·  ${tf("play_time", strings.duration(it.playTimeSeconds))}" } ?: t("no_instance_title"),
                        color = o.text, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        selected?.let { lastPlayedText(it) } ?: "",
                        color = o.textDim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (selected != null) {
                val running = sessions[selected.id]?.running?.value == true
                if (running) {
                    PrimaryButton(t("stop"), { app.stop(selected.id) }, icon = ObsiIcons.Stop)
                } else {
                    PrimaryButton(t("play"), { app.play(selected) }, icon = ObsiIcons.Play, modifier = Modifier.height(58.dp))
                }
            }
        }
    }
}

@Composable
private fun AccountChip(account: Account?, all: List<Account>, app: AppController) {
    val o = obsi()
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.clip(RoundedCornerShape(14.dp)).background(o.glass).clickable { if (all.isEmpty()) app.go(Screen.Accounts) else open = true }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (account != null) {
                LetterAvatar(account.username, 30.dp)
                Column {
                    Dim(t("playing_as"), size = 11)
                    Text(account.username, color = o.text, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                }
                Pill(if (account.isMicrosoft) t("account_ms") else t("account_offline"), if (account.isMicrosoft) o.good else o.warn)
            } else {
                Icon(ObsiIcons.Person, null, tint = o.textDim)
                Text(t("no_account"), color = o.textDim)
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = o.glassHigh) {
            all.forEach { a ->
                DropdownMenuItem(text = { Text(a.username, color = o.text) }, onClick = { app.selectAccount(a.id); open = false })
            }
            DropdownMenuItem(text = { Text(t("nav_accounts"), color = o.text) }, onClick = { open = false; app.go(Screen.Accounts) })
        }
    }
}
