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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import studio.obsifox.obsilauncher.R
import studio.obsifox.obsilauncher.app
import studio.obsifox.obsilauncher.core.accounts.Account
import studio.obsifox.obsilauncher.core.auth.MicrosoftAuth
import studio.obsifox.obsilauncher.core.cosmetics.SkinManager
import studio.obsifox.obsilauncher.ui.components.GlassCard
import studio.obsifox.obsilauncher.ui.components.ObsiButton
import studio.obsifox.obsilauncher.ui.components.ObsiGhostButton
import studio.obsifox.obsilauncher.ui.components.ObsiTextButton
import studio.obsifox.obsilauncher.ui.components.SectionTitle
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
    var msaBusy by remember { mutableStateOf(false) }
    var deviceCode by remember { mutableStateOf<MicrosoftAuth.DeviceCode?>(null) }
    var msaError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

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
            Row {
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
                ObsiGhostButton(
                    text = if (msaBusy) "…" else stringResourceCompat(R.string.accounts_microsoft),
                    onClick = {
                        if (msaBusy) return@ObsiGhostButton
                        msaBusy = true
                        msaError = null
                        scope.launch {
                            try {
                                deviceCode = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                    app.microsoft.beginDeviceCode()
                                }
                            } catch (e: Exception) {
                                msaError = e.message
                            } finally {
                                msaBusy = false
                            }
                        }
                    },
                    modifier = Modifier.padding(start = 10.dp),
                )
            }
            msaError?.let {
                Text(it, color = obsi.danger, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
            }
        }

        if (accounts.isEmpty()) {
            GlassCard(modifier = Modifier.padding(top = 14.dp)) {
                Text(stringResourceCompat(R.string.accounts_empty), color = obsi.textDim)
            }
        } else {
            // plain Column — a LazyColumn nested in this scrollable Column crashes
            Column(modifier = Modifier.padding(top = 14.dp)) {
                accounts.forEach { account ->
                    AccountCard(
                        account = account,
                        active = account.id == activeId,
                    )
                }
            }
        }
    }

    // Microsoft device-code dialog: shows the code + polls until confirmed
    deviceCode?.let { device ->
        AlertDialog(
            onDismissRequest = { deviceCode = null },
            title = { Text(stringResourceCompat(R.string.accounts_microsoft)) },
            text = {
                Column {
                    Text(stringResourceCompat(R.string.accounts_msa_step1), style = MaterialTheme.typography.bodyMedium)
                    Text(device.verificationUrl, color = obsi.accent, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Text(stringResourceCompat(R.string.accounts_msa_step2), style = MaterialTheme.typography.bodyMedium)
                    Text(device.userCode, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = obsi.accent)
                    Spacer(Modifier.height(8.dp))
                    Text(stringResourceCompat(R.string.accounts_msa_waiting), style = MaterialTheme.typography.labelMedium, color = obsi.textDim)
                }
            },
            confirmButton = {},
            dismissButton = { ObsiTextButton(stringResourceCompat(R.string.cancel), onClick = { deviceCode = null }) },
        )

        // polling effect
        val pollScope = rememberCoroutineScope()
        androidx.compose.runtime.LaunchedEffect(device.deviceCode) {
            pollScope.launch {
                try {
                    val profile = app.microsoft.login(device)
                    app.accounts.upsertMicrosoft(
                        name = profile.name,
                        uuid = profile.id,
                        accessToken = profile.accessToken,
                        refreshToken = profile.refreshToken,
                        expiresAt = profile.expiresAtMs,
                    )
                    deviceCode = null
                } catch (e: Exception) {
                    msaError = e.message
                    deviceCode = null
                }
            }
        }
    }
}

@Composable
private fun AccountCard(account: Account, active: Boolean) {
    val context = LocalContext.current
    val app = context.app
    val obsi = LocalObsi.current

    var expanded by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    val pickSkin = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@rememberLauncherForActivityResult
                val path = SkinManager.store(context, account.id, "skin", bytes)
                app.accounts.update(account.id) { it.copy(skinPath = path) }
                message = null
            }
        }
    }
    val pickCape = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@rememberLauncherForActivityResult
                val path = SkinManager.store(context, account.id, "cape", bytes)
                app.accounts.update(account.id) { it.copy(capePath = path) }
            }
        }
    }

    GlassCard(modifier = Modifier.padding(bottom = 10.dp)) {
        Row {
            Column(Modifier.weight(1f)) {
                Text(account.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                val subtitle = when {
                    account.isMicrosoft -> stringResourceCompat(R.string.accounts_type_microsoft)
                    active -> stringResourceCompat(R.string.accounts_active)
                    else -> account.offlineUuid()
                }
                Text(subtitle, style = MaterialTheme.typography.labelMedium, color = obsi.textDim)
            }
            if (!active) {
                ObsiGhostButton(stringResourceCompat(R.string.accounts_set_active), onClick = { app.accounts.setActive(account.id) })
            }
            ObsiGhostButton(
                if (expanded) "▲" else "▼",
                onClick = { expanded = !expanded },
            )
        }

        if (expanded) {
            Spacer(Modifier.height(10.dp))
            SectionTitle(stringResourceCompat(R.string.skins_title))
            Text(
                stringResourceCompat(R.string.skins_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = obsi.textDim,
            )
            Spacer(Modifier.height(8.dp))
            Row {
                ObsiGhostButton(stringResourceCompat(R.string.skins_pick_skin), onClick = { pickSkin.launch("image/*") })
                ObsiTextButton(stringResourceCompat(R.string.skins_clear_skin), onClick = {
                    SkinManager.clear(context, account.id, "skin")
                    app.accounts.update(account.id) { it.copy(skinPath = "") }
                }, enabled = account.skinPath.isNotEmpty())
            }
            Row {
                ObsiGhostButton(stringResourceCompat(R.string.skins_pick_cape), onClick = { pickCape.launch("image/*") })
                ObsiTextButton(stringResourceCompat(R.string.skins_clear_cape), onClick = {
                    SkinManager.clear(context, account.id, "cape")
                    app.accounts.update(account.id) { it.copy(capePath = "") }
                }, enabled = account.capePath.isNotEmpty())
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                Text(
                    stringResourceCompat(R.string.skins_model),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                ObsiGhostButton(
                    text = if (account.skinModel != "slim") "● ${stringResourceCompat(R.string.skins_model_classic)}" else "○ ${stringResourceCompat(R.string.skins_model_classic)}",
                    onClick = { app.accounts.update(account.id) { it.copy(skinModel = "classic") } },
                    enabled = account.skinModel == "slim",
                )
                ObsiGhostButton(
                    text = if (account.skinModel == "slim") "● ${stringResourceCompat(R.string.skins_model_slim)}" else "○ ${stringResourceCompat(R.string.skins_model_slim)}",
                    onClick = { app.accounts.update(account.id) { it.copy(skinModel = "slim") } },
                    enabled = account.skinModel != "slim",
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResourceCompat(R.string.skins_local_toggle), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        stringResourceCompat(R.string.skins_local_hint),
                        style = MaterialTheme.typography.labelMedium,
                        color = obsi.textDim,
                    )
                }
                Switch(
                    checked = account.localSkinEnabled,
                    onCheckedChange = { on ->
                        app.accounts.update(account.id) { it.copy(localSkinEnabled = on) }
                    },
                    colors = SwitchDefaults.colors(checkedTrackColor = obsi.accent),
                )
            }
            message?.let { Text(it, color = obsi.textDim, style = MaterialTheme.typography.bodyMedium) }

            Spacer(Modifier.height(8.dp))
            ObsiTextButton(stringResourceCompat(R.string.accounts_remove), onClick = { app.accounts.remove(account.id) })
        }
    }
}
