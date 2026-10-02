package studio.obsifox.launcher.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import studio.obsifox.launcher.core.auth.AuthService

/** Local (offline) accounts only - by design there is no Microsoft sign-in and no remote profile service. */
@Composable
fun AccountsScreen() {
    val app = LocalApp.current
    val accounts by app.core.accounts.flow.collectAsState()
    val settings by app.core.settings.flow.collectAsState()
    var showAdd by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(t("accounts_title")) {
            PrimaryButton(t("add_offline"), { showAdd = true }, icon = ObsiIcons.Add)
        }
        if (accounts.isEmpty()) {
            EmptyState(ObsiIcons.Person, t("no_accounts"), t("offline_note"))
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(accounts, key = { it.id }) { acc ->
                    val active = acc.id == (settings.selectedAccountId ?: accounts.first().id)
                    ObsiCard(Modifier.fillMaxWidth(), onClick = { app.selectAccount(acc.id) }, border = if (active) Obsi.orange else Obsi.line) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            LetterAvatar(acc.username, 48.dp)
                            Column(Modifier.weight(1f)) {
                                Text(acc.username, color = Obsi.text, fontWeight = FontWeight.Bold)
                                Dim(acc.uuid.chunked(8).first() + "…")
                            }
                            Pill(t("account_offline"), Obsi.yellow)
                            if (active) Pill(t("active"), Obsi.orange)
                            IconAction(ObsiIcons.Delete, t("remove_account"), { app.core.accounts.remove(acc.id) }, tint = Obsi.red)
                        }
                    }
                }
            }
        }
    }
    if (showAdd) AddOfflineDialog { showAdd = false }
}

@Composable
fun AddOfflineDialog(onDismiss: () -> Unit) {
    val app = LocalApp.current
    var name by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val valid = AuthService.isValidOfflineName(name.trim())
    WideDialog(t("add_offline"), onDismiss, width = 460.dp, confirm = {
        PrimaryButton(t("create"), {
            runCatching { app.core.auth.addOffline(name) }
                .onSuccess { acc -> app.selectAccount(acc.id); onDismiss() }
                .onFailure { error = it.message }
        }, enabled = valid)
    }) {
        LabeledField(t("offline_name"), name, { name = it; error = null }, hint = t("offline_hint"))
        error?.let { Text(it, color = Obsi.red) }
        Dim(t("offline_note"), maxLines = 3)
    }
}
