package studio.obsifox.obsilauncher.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import studio.obsifox.obsilauncher.R
import studio.obsifox.obsilauncher.app
import studio.obsifox.obsilauncher.ui.components.GlassCard
import studio.obsifox.obsilauncher.ui.components.ObsiButton
import studio.obsifox.obsilauncher.ui.components.ObsiGhostButton
import studio.obsifox.obsilauncher.ui.theme.LocalObsi

@Composable
fun AccountsScreen() {
    val context = LocalContext.current
    val app = context.app
    val obsi = LocalObsi.current

    val accounts by app.accounts.accounts.collectAsState()
    val activeId by app.accounts.activeId.collectAsState()
    var name by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
    ) {
        GlassCard {
            Text(stringResourceCompat(R.string.accounts_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResourceCompat(R.string.accounts_offline_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = obsi.textDim,
                modifier = Modifier.padding(top = 4.dp),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it; error = null },
                label = { Text(stringResourceCompat(R.string.accounts_name_hint)) },
                isError = error != null,
                supportingText = { error?.let { Text(it) } },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            ObsiButton(
                text = stringResourceCompat(R.string.accounts_add),
                onClick = {
                    try {
                        app.accounts.add(name)
                        name = ""
                        error = null
                    } catch (e: Exception) {
                        error = e.message
                    }
                },
                enabled = name.isNotBlank(),
            )
        }

        if (accounts.isEmpty()) {
            GlassCard(modifier = Modifier.padding(top = 14.dp)) {
                Text(stringResourceCompat(R.string.accounts_empty), color = obsi.textDim)
            }
        } else {
            LazyColumn(modifier = Modifier.padding(top = 14.dp)) {
                items(accounts, key = { it.id }) { account ->
                    GlassCard {
                        Row {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    account.name,
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                if (account.id == activeId) {
                                    Text(
                                        stringResourceCompat(R.string.accounts_active),
                                        color = obsi.accent,
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                }
                                Text(
                                    account.offlineUuid(),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = obsi.textDim,
                                )
                            }
                            if (account.id != activeId) {
                                ObsiGhostButton(
                                    stringResourceCompat(R.string.accounts_set_active),
                                    onClick = { app.accounts.setActive(account.id) },
                                )
                            }
                            ObsiGhostButton(
                                stringResourceCompat(R.string.accounts_remove),
                                onClick = { app.accounts.remove(account.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}
