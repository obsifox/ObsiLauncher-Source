package studio.obsifox.launcher.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import studio.obsifox.launcher.core.auth.Account
import studio.obsifox.launcher.core.auth.AccountType
import studio.obsifox.launcher.core.auth.AuthService
import studio.obsifox.launcher.core.auth.DeviceCodeInfo

@Composable
fun AccountsScreen() {
    val app = LocalApp.current
    val accounts by app.core.accounts.flow.collectAsState()
    val settings by app.core.settings.flow.collectAsState()
    var showOffline by remember { mutableStateOf(false) }
    var showMs by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(t("accounts_title")) {
            SoftButton(t("add_offline"), { showOffline = true }, icon = ObsiIcons.Person)
            HSpace(10)
            PrimaryButton(t("add_microsoft"), { showMs = true }, icon = ObsiIcons.Add)
        }
        if (!app.core.microsoftConfigured) {
            ObsiCard(Modifier.fillMaxWidth(), border = obsi().warn.copy(alpha = 0.5f)) {
                Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Icon(ObsiIcons.Info, null, tint = obsi().warn)
                    Dim(t("ms_not_configured"), size = 13, maxLines = 4)
                }
            }
        }
        if (accounts.isEmpty()) {
            EmptyState(ObsiIcons.Person, t("no_accounts"), t("offline_note"))
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(accounts, key = { it.id }) { acc ->
                    val active = acc.id == (settings.selectedAccountId ?: accounts.first().id)
                    ObsiCard(Modifier.fillMaxWidth(), onClick = { app.selectAccount(acc.id) }, border = if (active) obsi().accent else obsi().line) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            LetterAvatar(acc.username, 48.dp)
                            Column(Modifier.weight(1f)) {
                                Text(acc.username, color = obsi().text, fontWeight = FontWeight.Bold)
                                Dim(acc.uuid.chunked(8).first() + "…")
                            }
                            Pill(if (acc.type == AccountType.MICROSOFT) t("account_ms") else t("account_offline"), if (acc.isMicrosoft) obsi().good else obsi().warn)
                            if (active) Pill(t("active"), obsi().accent)
                            IconAction(ObsiIcons.Delete, t("remove_account"), { app.core.accounts.remove(acc.id) }, tint = obsi().bad)
                        }
                    }
                }
            }
        }
    }
    if (showOffline) OfflineDialog { showOffline = false }
    if (showMs) MicrosoftDialog { showMs = false }
}

@Composable
private fun OfflineDialog(onDismiss: () -> Unit) {
    val app = LocalApp.current
    var name by remember { mutableStateOf("") }
    val valid = AuthService.isValidOfflineName(name.trim())
    WideDialog(t("add_offline"), onDismiss, width = 460.dp, confirm = {
        PrimaryButton(t("create"), {
            val acc = app.core.auth.addOffline(name)
            app.selectAccount(acc.id)
            onDismiss()
        }, enabled = valid)
    }) {
        LabeledField(t("offline_name"), name, { name = it }, hint = t("offline_hint"))
        Dim(t("offline_note"), maxLines = 3)
    }
}

@Composable
private fun MicrosoftDialog(onDismiss: () -> Unit) {
    val app = LocalApp.current
    var info by remember { mutableStateOf<DeviceCodeInfo?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var account by remember { mutableStateOf<Account?>(null) }

    LaunchedEffect(Unit) {
        try {
            val code = app.core.auth.microsoft.startDeviceCode()
            info = code
            SystemOpen.browse(code.verificationUri)
            val acc = app.core.auth.microsoft.awaitLogin(code)
            app.core.accounts.upsert(acc)
            app.selectAccount(acc.id)
            account = acc
            app.toast(app.s.fmt("ms_success", acc.username))
            onDismiss()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: app.s["error"]
        }
    }

    WideDialog(t("ms_title"), onDismiss, width = 520.dp) {
        val i = info
        when {
            error != null -> {
                Text(t("ms_failed"), color = obsi().bad, fontWeight = FontWeight.Bold)
                Text(error!!, color = obsi().textDim)
            }
            i == null -> Box(Modifier.fillMaxWidth().padding(24.dp), Alignment.Center) { CircularProgressIndicator(color = obsi().accent) }
            else -> {
                Text(t("ms_instructions"), color = obsi().textDim)
                Text(i.verificationUri, color = obsi().secondary, fontWeight = FontWeight.Medium)
                ObsiCard(Modifier.fillMaxWidth(), color = obsi().glassInput) {
                    Text(i.userCode, Modifier.fillMaxWidth().padding(18.dp), color = obsi().accent, fontSize = 34.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, letterSpacing = 4.sp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SoftButton(t("copy"), { copyToClipboard(i.userCode); app.toast(app.s["copied"]) }, icon = ObsiIcons.Copy)
                    SoftButton(t("ms_open_page"), { SystemOpen.browse(i.verificationUri) }, icon = ObsiIcons.OpenInNew)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircularProgressIndicator(Modifier.padding(2.dp), color = obsi().accent, strokeWidth = 2.dp)
                    Dim(t("ms_waiting"))
                }
            }
        }
    }
}
