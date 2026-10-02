package studio.obsifox.obsilauncher.ui.screens

import androidx.compose.animation.core.animateFloatAsState
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
import studio.obsifox.obsilauncher.core.accounts.AccountStore
import studio.obsifox.obsilauncher.core.game.GameState
import studio.obsifox.obsilauncher.ui.components.GlassCard
import studio.obsifox.obsilauncher.ui.components.ObsiButton
import studio.obsifox.obsilauncher.ui.components.ObsiGhostButton
import studio.obsifox.obsilauncher.ui.theme.LocalObsi

@Composable
fun HomeScreen(
    onPlay: () -> Unit,
    onPickVersion: () -> Unit,
    onPickAccount: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.app
    val obsi = LocalObsi.current

    val selected by app.settings.selectedVersion.collectAsState()
    val accounts by app.accounts.accounts.collectAsState()
    val activeAccount by app.accounts.activeId.collectAsState()
    val packs by app.runtimePacks.packs.collectAsState()
    val runtimePack by app.settings.runtimePack.collectAsState()
    val gameState by app.gameManager.state.collectAsState()
    val installing by app.installer.state.collectAsState()

    val hasVersion = selected.isNotBlank() && app.installer.isInstalled(selected)
    val account = accounts.firstOrNull { it.id == activeAccount }
    val pack = packs.firstOrNull { it.name == runtimePack }

    val pulse by animateFloatAsState(if (gameState == GameState.RUNNING) 1f else 0.85f, label = "pulse")

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
                    gameState == GameState.PREPARING -> stringResourceCompat(R.string.home_launching, selected)
                    hasVersion -> stringResourceCompat(R.string.home_version_ready, selected)
                    else -> stringResourceCompat(R.string.home_no_version)
                },
                style = MaterialTheme.typography.titleMedium,
                color = obsi.textDim,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = selected.ifBlank { "—" },
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
            )
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
                    stringResourceCompat(R.string.nav_versions),
                    style = MaterialTheme.typography.labelMedium,
                    color = obsi.textDim,
                )
                Text(
                    "${app.installer.installed().size}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
                ObsiGhostButton(
                    text = stringResourceCompat(R.string.versions_select),
                    onClick = onPickVersion,
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
