package studio.obsifox.obsilauncher.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import studio.obsifox.obsilauncher.R
import studio.obsifox.obsilauncher.app
import studio.obsifox.obsilauncher.core.game.InstallState
import studio.obsifox.obsilauncher.core.game.McVersion
import studio.obsifox.obsilauncher.ui.components.GlassCard
import studio.obsifox.obsilauncher.ui.components.ObsiButton
import studio.obsifox.obsilauncher.ui.components.ObsiGhostButton
import studio.obsifox.obsilauncher.ui.components.ObsiTextButton
import studio.obsifox.obsilauncher.ui.components.ProgressRow
import studio.obsifox.obsilauncher.ui.theme.LocalObsi
import kotlinx.coroutines.launch

@Composable
fun VersionsScreen() {
    val context = LocalContext.current
    val app = context.app
    val obsi = LocalObsi.current

    var tab by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val tabs = listOf(
        stringResourceCompat(R.string.versions_installed),
        stringResourceCompat(R.string.versions_releases),
        stringResourceCompat(R.string.versions_snapshots),
        stringResourceCompat(R.string.versions_old),
    )

    LaunchedEffect(Unit) { app.manifest.refresh(force = false) }

    val manifestLoading by app.manifest.loading.collectAsState()
    val allVersions by app.manifest.versions.collectAsState()
    val releases = allVersions.filter { it.type == "release" }
    val snapshots = allVersions.filter { it.type == "snapshot" }
    val old = allVersions.filter { it.isOld }
    val selected by app.settings.selectedVersion.collectAsState()
    val installState by app.installer.state.collectAsState()

    Column(modifier = Modifier.fillMaxWidth()) {
        TabRow(
            selectedTabIndex = tab,
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            contentColor = obsi.accent,
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = tab == index,
                    onClick = { tab = index },
                    text = { Text(title) },
                )
            }
        }
        Spacer(Modifier.height(12.dp))

        when (installState) {
            is InstallState.Running -> InstallProgressCard(installState as InstallState.Running)
            is InstallState.Failed -> InstallFailedCard(installState as InstallState.Failed)
            else -> {}
        }

        when (tab) {
            0 -> InstalledList(
                selected = selected,
                onSelect = { app.settings.selectedVersionValue = it },
                onDelete = {
                    val dir = studio.obsifox.obsilauncher.core.Paths.versionDir(context, it)
                    dir.deleteRecursively()
                    if (selected == it) app.settings.selectedVersionValue = ""
                },
            )
            1 -> RemoteList(releases, manifestLoading, selected) { v -> scope.launch { app.installer.install(v, app.settings) } }
            2 -> RemoteList(snapshots, manifestLoading, selected) { v -> scope.launch { app.installer.install(v, app.settings) } }
            3 -> RemoteList(old, manifestLoading, selected) { v -> scope.launch { app.installer.install(v, app.settings) } }
        }
    }
}

@Composable
private fun InstallProgressCard(state: InstallState.Running) {
    val obsi = LocalObsi.current
    val context = LocalContext.current
    GlassCard(modifier = Modifier.padding(bottom = 12.dp)) {
        val label = when (state.step) {
            "manifest" -> stringResourceCompat(R.string.dl_step_manifest)
            "client" -> stringResourceCompat(R.string.dl_step_client)
            "libraries" -> stringResourceCompat(R.string.dl_step_libraries, state.done + 1, state.total)
            "natives" -> stringResourceCompat(R.string.dl_step_natives)
            "assets" -> stringResourceCompat(R.string.dl_step_assets, state.done + 1, state.total)
            else -> state.step
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { ProgressRow(label, state.fraction) }
            ObsiTextButton(
                stringResourceCompat(R.string.dl_cancel),
                onClick = { context.app.installer.cancel() },
            )
        }
        Text(
            text = stringResourceCompat(R.string.dl_installing, ""),
            style = MaterialTheme.typography.labelMedium,
            color = obsi.textDim,
        )
    }
}

@Composable
private fun InstallFailedCard(state: InstallState.Failed) {
    val context = LocalContext.current
    GlassCard(modifier = Modifier.padding(bottom = 12.dp)) {
        Text(
            stringResourceCompat(R.string.dl_failed, state.message ?: "unknown"),
            style = MaterialTheme.typography.bodyMedium,
        )
        ObsiGhostButton(stringResourceCompat(R.string.dl_retry), onClick = {
            context.app.installer.state.value = InstallState.Idle
        })
    }
}

@Composable
private fun InstalledList(selected: String, onSelect: (String) -> Unit, onDelete: (String) -> Unit) {
    val context = LocalContext.current
    val app = context.app
    val installed = app.installer.installed()
    val obsi = LocalObsi.current
    if (installed.isEmpty()) {
        GlassCard { Text(stringResourceCompat(R.string.versions_empty_installed), color = obsi.textDim) }
        return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(installed) { id ->
            GlassCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(
                        Modifier
                            .weight(1f)
                            .clickable { onSelect(id) },
                    ) {
                        Text(id, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        if (id == selected) {
                            Text(
                                stringResourceCompat(R.string.versions_selected),
                                style = MaterialTheme.typography.labelMedium,
                                color = obsi.accent,
                            )
                        }
                    }
                    ObsiGhostButton(stringResourceCompat(R.string.versions_select), onClick = { onSelect(id) })
                    ObsiGhostButton(
                        stringResourceCompat(R.string.versions_delete),
                        onClick = { onDelete(id) },
                        enabled = id != selected,
                    )
                }
            }
        }
    }
}

@Composable
private fun RemoteList(
    versions: List<McVersion>,
    loading: Boolean,
    selected: String,
    onInstall: (McVersion) -> Unit,
) {
    val obsi = LocalObsi.current
    if (versions.isEmpty()) {
        GlassCard {
            Text(
                if (loading) "…" else stringResourceCompat(R.string.versions_empty_remote),
                color = obsi.textDim,
            )
        }
        return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(versions, key = { it.id }) { v ->
            GlassCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(v.id, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            v.type + (v.releaseTime.takeIf { it.isNotEmpty() }?.let { " · ${it.substring(0, 10)}" } ?: ""),
                            style = MaterialTheme.typography.labelMedium,
                            color = obsi.textDim,
                        )
                    }
                    if (v.id == selected) {
                        Text(
                            stringResourceCompat(R.string.versions_selected),
                            color = obsi.accent,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    } else {
                        ObsiButton(stringResourceCompat(R.string.versions_install), onClick = { onInstall(v) })
                    }
                }
            }
        }
    }
}
