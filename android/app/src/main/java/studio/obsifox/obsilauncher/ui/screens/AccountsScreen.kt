package studio.obsifox.obsilauncher.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import studio.obsifox.obsilauncher.R
import studio.obsifox.obsilauncher.app
import studio.obsifox.obsilauncher.core.accounts.Account
import studio.obsifox.obsilauncher.core.auth.MicrosoftAuth
import studio.obsifox.obsilauncher.core.cosmetics.SkinManager
import studio.obsifox.obsilauncher.ui.components.ObsiButton
import studio.obsifox.obsilauncher.ui.components.ObsiGhostButton
import studio.obsifox.obsilauncher.ui.components.ObsiTextButton
import studio.obsifox.obsilauncher.ui.components.PlayerHead
import studio.obsifox.obsilauncher.ui.theme.LocalObsi

/**
 * Accounts — the active player's head sits big on top (never a letter
 * placeholder); every account row carries its own head. Expanding a row
 * reveals skin / cape management and the model switch.
 */
@Composable
fun AccountsScreen() {
    val context = LocalContext.current
    val app = context.app
    val obsi = LocalObsi.current

    val accounts by app.accounts.accounts.collectAsState()
    val activeId by app.accounts.activeId.collectAsState()
    val active = accounts.firstOrNull { it.id == activeId }

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
        // active profile card --------------------------------------------------
        Box(
            Modifier
                .padding(horizontal = 14.dp, vertical = 6.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0x4D140F0C))
                .border(1.dp, Color(0x24FFFFFF), RoundedCornerShape(18.dp))
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(78.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .border(1.5.dp, obsi.accent.copy(alpha = 0.55f), RoundedCornerShape(14.dp)),
                ) {
                    PlayerHead(account = active, modifier = Modifier.size(78.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        active?.name ?: stringResourceCompat(R.string.accounts_no_active),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFF4EFEA),
                    )
                    Text(
                        when {
                            active == null -> stringResourceCompat(R.string.accounts_add_first)
                            active.isMicrosoft -> stringResourceCompat(R.string.accounts_type_microsoft)
                            else -> stringResourceCompat(R.string.accounts_type_offline)
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = obsi.textDim,
                    )
                }
            }
        }

        // add / login ------------------------------------------------------------
        Box(
            Modifier
                .padding(horizontal = 14.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0x3D140F0C))
                .border(1.dp, Color(0x1FFFFFFF), RoundedCornerShape(16.dp))
                .padding(14.dp),
        ) {
            Column {
                Text(
                    stringResourceCompat(R.string.accounts_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    stringResourceCompat(R.string.accounts_offline_hint),
                    style = MaterialTheme.typography.labelMedium,
                    color = obsi.textDim,
                )
                Spacer(Modifier.height(10.dp))
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
        }

        // account bars -----------------------------------------------------------
        if (accounts.isEmpty()) {
            Text(
                stringResourceCompat(R.string.accounts_empty),
                color = obsi.textDim,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp),
            )
        } else {
            Column(modifier = Modifier.padding(top = 12.dp)) {
                accounts.forEach { account ->
                    AccountBar(
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
private fun AccountBar(account: Account, active: Boolean) {
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

    Column(
        Modifier
            .padding(horizontal = 14.dp, vertical = 4.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0x3D140F0C))
            .border(
                1.dp,
                if (active) obsi.accent.copy(alpha = 0.55f) else Color(0x1FFFFFFF),
                RoundedCornerShape(14.dp),
            )
            .clickable { expanded = !expanded }
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(10.dp)),
            ) {
                PlayerHead(account = account, modifier = Modifier.size(42.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(account.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = Color(0xFFF4EFEA))
                Text(
                    when {
                        account.isMicrosoft -> stringResourceCompat(R.string.accounts_type_microsoft)
                        active -> stringResourceCompat(R.string.accounts_active)
                        else -> account.offlineUuid()
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = obsi.textDim,
                )
            }
            if (active) {
                Text(
                    stringResourceCompat(R.string.accounts_active),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier
                        .clip(RoundedCornerShape(7.dp))
                        .background(obsi.accent)
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
            } else {
                ObsiGhostButton(stringResourceCompat(R.string.accounts_set_active), onClick = { app.accounts.setActive(account.id) })
            }
            ObsiGhostButton(if (expanded) "▲" else "▼", onClick = { expanded = !expanded })
        }

        if (expanded) {
            Spacer(Modifier.height(10.dp))
            Text(stringResourceCompat(R.string.skins_title), style = MaterialTheme.typography.titleSmall, color = obsi.accent)
            Text(
                stringResourceCompat(R.string.skins_hint),
                style = MaterialTheme.typography.labelMedium,
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
                    text = stringResourceCompat(R.string.skins_model_classic),
                    onClick = { app.accounts.update(account.id) { it.copy(skinModel = "classic") } },
                    enabled = account.skinModel == "slim",
                )
                ObsiGhostButton(
                    text = stringResourceCompat(R.string.skins_model_slim),
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
