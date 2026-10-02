package studio.obsifox.obsilauncher.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import studio.obsifox.obsilauncher.R
import studio.obsifox.obsilauncher.app
import studio.obsifox.obsilauncher.core.game.GameState
import studio.obsifox.obsilauncher.core.instance.Instance
import studio.obsifox.obsilauncher.ui.components.GlassCard
import studio.obsifox.obsilauncher.ui.components.ObsiButton
import studio.obsifox.obsilauncher.ui.components.ObsiGhostButton
import studio.obsifox.obsilauncher.ui.components.ObsiTextButton
import studio.obsifox.obsilauncher.ui.components.SectionTitle
import studio.obsifox.obsilauncher.ui.theme.LocalObsi

@Composable
fun HomeScreen(
    onPlay: () -> Unit,
    onPickVersion: () -> Unit,
    onPickAccount: () -> Unit,
    onOpenConsole: () -> Unit,
    onOpenInstance: () -> Unit,
    onOpenBrowse: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.app
    val obsi = LocalObsi.current

    val instances by app.instances.instances.collectAsState()
    val activeId by app.instances.activeId.collectAsState()
    val accounts by app.accounts.accounts.collectAsState()
    val activeAccountId by app.accounts.activeId.collectAsState()
    val packs by app.runtimePacks.packs.collectAsState()
    val runtimePack by app.settings.runtimePack.collectAsState()
    val gameState by app.gameManager.state.collectAsState()
    val installing by app.installer.state.collectAsState()

    val active = instances.firstOrNull { it.id == activeId }
    val account = accounts.firstOrNull { it.id == activeAccountId }
    val pack = packs.firstOrNull { it.name == runtimePack } ?: packs.firstOrNull()
    val hasVersion = active != null && app.installer.isInstalled(active.versionId)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
    ) {
        GlassCard {
            Text(
                text = when {
                    gameState == GameState.RUNNING -> stringResourceCompat(R.string.console_running)
                    gameState == GameState.PREPARING -> stringResourceCompat(R.string.home_launching, active?.name ?: "")
                    hasVersion -> stringResourceCompat(R.string.home_version_ready, active?.name ?: "")
                    else -> stringResourceCompat(R.string.home_no_version)
                },
                style = MaterialTheme.typography.titleMedium,
                color = obsi.textDim,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = active?.name ?: "—",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
            )
            if (active != null) {
                Text(
                    text = loaderBadge(active),
                    style = MaterialTheme.typography.labelMedium,
                    color = obsi.accent,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Spacer(Modifier.height(18.dp))

            val canPlay = hasVersion && account != null && pack != null &&
                gameState != GameState.RUNNING && gameState != GameState.PREPARING &&
                installing !is studio.obsifox.obsilauncher.core.game.InstallState.Running
            ObsiButton(
                text = stringResourceCompat(R.string.home_play),
                onClick = onPlay,
                enabled = canPlay,
                modifier = Modifier.fillMaxWidth(),
            )
            if (!canPlay) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = when {
                        !hasVersion -> stringResourceCompat(R.string.home_no_version)
                        account == null -> stringResourceCompat(R.string.home_no_account)
                        pack == null -> stringResourceCompat(R.string.home_missing_runtime)
                        else -> ""
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = obsi.textDim,
                )
            }
            if (gameState == GameState.RUNNING || gameState == GameState.EXITED) {
                ObsiTextButton(stringResourceCompat(R.string.console_title), onClick = onOpenConsole)
            }
        }

        // instance switcher -----------------------------------------------------
        SectionTitle(stringResourceCompat(R.string.home_instances))
        if (instances.isEmpty()) {
            GlassCard {
                Text(stringResourceCompat(R.string.home_no_instances), color = obsi.textDim)
                ObsiGhostButton(stringResourceCompat(R.string.versions_install), onClick = onPickVersion, modifier = Modifier.padding(top = 8.dp))
            }
        } else {
            instances.forEach { instance ->
                InstanceChip(
                    instance = instance,
                    selected = instance.id == activeId,
                    onSelect = {
                        app.instances.setActive(instance.id)
                        app.settings.selectedVersionValue = instance.versionId
                    },
                    onDetail = onOpenInstance,
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            GlassCard(modifier = Modifier.weight(1f)) {
                Text(
                    stringResourceCompat(R.string.nav_accounts),
                    style = MaterialTheme.typography.labelMedium,
                    color = obsi.textDim,
                )
                Text(
                    account?.name ?: "—",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
                ObsiGhostButton(
                    text = if (account == null) stringResourceCompat(R.string.accounts_add)
                    else stringResourceCompat(R.string.accounts_set_active),
                    onClick = onPickAccount,
                )
            }
            GlassCard(modifier = Modifier.weight(1f)) {
                Text(
                    stringResourceCompat(R.string.nav_browse),
                    style = MaterialTheme.typography.labelMedium,
                    color = obsi.textDim,
                )
                Text(
                    "Modrinth",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
                ObsiGhostButton(
                    text = stringResourceCompat(R.string.browse_open),
                    onClick = onOpenBrowse,
                )
            }
        }

        GlassCard(modifier = Modifier.padding(top = 14.dp)) {
            Text(
                stringResourceCompat(R.string.home_wallpaper_note),
                style = MaterialTheme.typography.bodyMedium,
                color = obsi.textDim,
            )
        }
    }
}

@Composable
private fun InstanceChip(instance: Instance, selected: Boolean, onSelect: () -> Unit, onDetail: () -> Unit) {
    val obsi = LocalObsi.current
    GlassCard(modifier = Modifier.padding(bottom = 10.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable { onSelect() },
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    instance.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (selected) obsi.accent else obsi.text,
                )
                Text(
                    loaderBadge(instance),
                    style = MaterialTheme.typography.labelMedium,
                    color = obsi.textDim,
                )
            }
            ObsiTextButton(stringResourceCompat(R.string.instance_details), onClick = onDetail)
        }
    }
}

internal fun loaderBadge(instance: Instance): String = when (instance.loaderType.name) {
    "VANILLA" -> instance.mcVersion
    else -> "${instance.mcVersion} · ${instance.loaderType.display}" +
        (instance.loaderVersion?.let { " $it" } ?: "")
}
