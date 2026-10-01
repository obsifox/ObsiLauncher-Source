package studio.obsifox.launcher.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen() {
    val app = LocalApp.current
    val instances by app.core.instances.flow.collectAsState()
    val settings by app.core.settings.flow.collectAsState()
    val accounts by app.core.accounts.flow.collectAsState()
    val sessions by app.sessions.collectAsState()
    val strings by app.strings.collectAsState()

    val selected = instances.firstOrNull { it.id == settings.selectedInstanceId } ?: instances.firstOrNull()
    val account = accounts.firstOrNull { it.id == settings.selectedAccountId } ?: accounts.firstOrNull()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        // ---- hero
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF2B1B52), Color(0xFF1B1233), Color(0xFF3A1F12))))
                .border(1.dp, Obsi.line, RoundedCornerShape(24.dp)).padding(28.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FoxMark(34.dp)
                    HSpace(12)
                    Column {
                        Text(t("home_title"), style = MaterialTheme.typography.headlineSmall, color = Obsi.text)
                        Dim(t("home_sub"))
                    }
                }
                if (selected == null) {
                    EmptyState(ObsiIcons.Apps, t("no_instance_title"), t("no_instance_text")) {
                        PrimaryButton(t("create_instance"), { app.go(Screen.Instances) }, icon = ObsiIcons.Add)
                    }
                } else {
                    val running = sessions[selected.id]?.running?.value == true
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        InstanceIcon(selected, 84.dp)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(selected.name, style = MaterialTheme.typography.titleLarge, color = Obsi.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(selected.subtitle, color = Obsi.orange, fontWeight = FontWeight.Medium)
                            Dim(lastPlayedText(selected) + if (selected.playTimeSeconds > 0) "  ·  " + tf("play_time", strings.duration(selected.playTimeSeconds)) else "")
                        }
                        if (running) {
                            SoftButton(t("stop"), { app.stop(selected.id) }, icon = ObsiIcons.Stop, danger = true)
                            HSpace(8)
                            PrimaryButton(t("nav_console"), { app.go(Screen.Console) }, icon = ObsiIcons.Terminal)
                        } else {
                            PrimaryButton(t("play"), { app.play(selected) }, icon = ObsiIcons.Play, modifier = Modifier.padding(4.dp))
                        }
                    }
                    AccountChip(account, accounts, app)
                }
            }
        }

        // ---- other profiles
        if (instances.size > 1) {
            SectionHeader(t("recent"))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                instances.take(9).forEach { inst ->
                    ObsiCard(Modifier.width(262.dp), onClick = { app.selectInstance(inst.id) },
                        border = if (inst.id == selected?.id) Obsi.orange else Obsi.line) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            InstanceIcon(inst, 44.dp)
                            Column {
                                Text(inst.name, color = Obsi.text, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                                Dim(inst.subtitle)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountChip(account: Account?, all: List<Account>, app: AppController) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.clip(RoundedCornerShape(14.dp)).background(Color(0x33000000)).clickable { if (all.isEmpty()) app.go(Screen.Accounts) else open = true }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (account != null) {
                LetterAvatar(account.username, 30.dp)
                Column {
                    Dim(t("playing_as"), size = 11)
                    Text(account.username, color = Obsi.text, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                }
                Pill(if (account.isMicrosoft) t("account_ms") else t("account_offline"), if (account.isMicrosoft) Obsi.green else Obsi.yellow)
            } else {
                Icon(ObsiIcons.Person, null, tint = Obsi.textDim)
                Text(t("no_account"), color = Obsi.textDim)
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            all.forEach { a ->
                DropdownMenuItem(text = { Text(a.username) }, onClick = { app.selectAccount(a.id); open = false })
            }
            DropdownMenuItem(text = { Text(t("nav_accounts")) }, onClick = { open = false; app.go(Screen.Accounts) })
        }
    }
}
