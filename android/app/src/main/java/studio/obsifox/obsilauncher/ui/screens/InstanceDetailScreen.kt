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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import studio.obsifox.obsilauncher.core.game.InstallState
import studio.obsifox.obsilauncher.core.modrinth.ContentFile
import studio.obsifox.obsilauncher.core.modrinth.ProjectKind
import studio.obsifox.obsilauncher.ui.components.GlassCard
import studio.obsifox.obsilauncher.ui.components.KeyValueRow
import studio.obsifox.obsilauncher.ui.components.ObsiButton
import studio.obsifox.obsilauncher.ui.components.ObsiGhostButton
import studio.obsifox.obsilauncher.ui.components.ObsiTextButton
import studio.obsifox.obsilauncher.ui.components.ProgressRow
import studio.obsifox.obsilauncher.ui.components.SectionTitle
import studio.obsifox.obsilauncher.ui.theme.LocalObsi

/**
 * Per-instance management: custom name, memory/JVM overrides, file repair and
 * the installed content manager (mods / resource packs / shader packs) with
 * dependency-aware deletion — files that are prerequisites of other installed
 * content cannot be deleted (grayed out, with the "required by" info).
 */
@Composable
fun InstanceDetailScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val app = context.app
    val obsi = LocalObsi.current
    val scope = rememberCoroutineScope()

    val instances by app.instances.instances.collectAsState()
    val active = instances.firstOrNull { it.id == app.instances.activeId.value }

    if (active == null) {
        GlassCard { Text(stringResourceCompat(R.string.home_no_instances), color = obsi.textDim) }
        ObsiTextButton(stringResourceCompat(R.string.cancel), onClick = onClose)
        return
    }

    var tab by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        stringResourceCompat(R.string.instance_tab_content),
        stringResourceCompat(R.string.instance_tab_settings),
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(active.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(loaderBadge(active), style = MaterialTheme.typography.labelMedium, color = obsi.textDim)
            }
            ObsiTextButton(stringResourceCompat(R.string.cancel), onClick = onClose)
        }
        Spacer(Modifier.height(8.dp))
        TabRow(
            selectedTabIndex = tab,
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            contentColor = obsi.accent,
        ) {
            tabs.forEachIndexed { index, title -> Tab(selected = tab == index, onClick = { tab = index }, text = { Text(title) }) }
        }
        Spacer(Modifier.height(12.dp))

        when (tab) {
            0 -> ContentManagerPane(active.versionId)
            1 -> InstanceSettingsPane(active)
        }
    }
}

@Composable
private fun ContentManagerPane(versionId: String) {
    val context = LocalContext.current
    val app = context.app
    val obsi = LocalObsi.current
    val scope = rememberCoroutineScope()

    var kindIndex by remember { mutableIntStateOf(0) }
    val kinds = listOf(ProjectKind.MOD, ProjectKind.RESOURCEPACK, ProjectKind.SHADER)
    val kind = kinds[kindIndex]

    var tick by remember { mutableIntStateOf(0) }
    val files = remember(kind, tick) { app.modrinth.content.list(versionId, kind) }
    var info by remember { mutableStateOf<ContentFile?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    Column {
        TabRow(
            selectedTabIndex = kindIndex,
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            contentColor = obsi.accent,
        ) {
            kinds.forEachIndexed { index, k ->
                Tab(
                    selected = kindIndex == index,
                    onClick = { kindIndex = index },
                    text = {
                        Text(
                            when (k) {
                                ProjectKind.MOD -> stringResourceCompat(R.string.browse_mods)
                                ProjectKind.RESOURCEPACK -> stringResourceCompat(R.string.browse_resourcepacks)
                                ProjectKind.SHADER -> stringResourceCompat(R.string.browse_shaders)
                                else -> k.apiType
                            },
                            maxLines = 1,
                        )
                    },
                )
            }
        }
        Spacer(Modifier.height(10.dp))

        Row {
            ObsiGhostButton(stringResourceCompat(R.string.content_check_updates), onClick = {
                scope.launch {
                    message = null
                    val updated = runCatching {
                        val filesList = app.modrinth.content.list(versionId, ProjectKind.MOD).filter { it.entry?.versionId != null }
                        var count = 0
                        for (f in filesList) {
                            val entry = f.entry ?: continue
                            val loaders = app.modrinth.content.compatibleLoaders(versionId)
                            val mcVersion = app.modrinth.content.mcVersionOf(versionId)
                            val latest = runCatching {
                                app.modrinth.api.versions(entry.projectId, loaders, listOf(mcVersion))
                            }.getOrNull()?.firstOrNull { it.versionType == "release" } ?: continue
                            if (latest.id != entry.versionId) {
                                app.modrinth.forInstance(versionId)
                                val project = runCatching { app.modrinth.api.project(entry.projectId) }.getOrNull() ?: continue
                                app.modrinth.installVersion(project, latest, explicit = entry.explicit)
                                count++
                            }
                        }
                        context.getString(R.string.content_updates_done, count)
                    }.getOrElse { it.message ?: "error" }
                    message = updated
                    tick++
                }
            })
        }

        message?.let {
            GlassCard(modifier = Modifier.padding(vertical = 8.dp)) { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }

        if (files.isEmpty()) {
            GlassCard {
                Text(
                    when (kind) {
                        ProjectKind.MOD -> stringResourceCompat(R.string.content_empty_mods)
                        ProjectKind.RESOURCEPACK -> stringResourceCompat(R.string.content_empty_rp)
                        ProjectKind.SHADER -> stringResourceCompat(R.string.content_empty_shaders)
                        else -> stringResourceCompat(R.string.content_empty_mods)
                    },
                    color = obsi.textDim,
                )
                Text(stringResourceCompat(R.string.content_empty_hint), style = MaterialTheme.typography.labelMedium, color = obsi.textDim)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(files, key = { it.fileName }) { file ->
                    val lockedBy = remember(file.fileName, tick) { app.modrinth.content.requiredBy(versionId, file) }
                    ContentRow(
                        file = file,
                        lockedBy = lockedBy,
                        onToggle = { enabled ->
                            app.modrinth.content.setEnabled(versionId, file, enabled)
                            tick++
                        },
                        onDelete = {
                            app.modrinth.content.remove(versionId, file)
                            tick++
                        },
                        onShowInfo = { info = file },
                    )
                }
            }
        }
    }

    info?.let { file ->
        AlertDialog(
            onDismissRequest = { info = null },
            title = { Text(file.entry?.title ?: file.fileName) },
            text = {
                Column {
                    Text(
                        stringResourceCompat(R.string.content_locked_info, file.fileName),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(file.lockedInfo(), style = MaterialTheme.typography.bodyMedium, color = obsi.textDim)
                }
            },
            confirmButton = { ObsiTextButton(stringResourceCompat(R.string.ok), onClick = { info = null }) },
        )
    }
}

private fun ContentFile.lockedInfo(): String {
    val e = entry ?: return fileName
    return "$kind · ${e.versionNumber} · project ${e.projectId.take(8)}"
}

@Composable
private fun ContentRow(
    file: ContentFile,
    lockedBy: List<String>,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onShowInfo: () -> Unit,
) {
    val obsi = LocalObsi.current
    val lock = lockedBy.isNotEmpty()
    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                Modifier
                    .weight(1f)
                    .clickable { if (lock) onShowInfo() },
            ) {
                Text(
                    file.entry?.title ?: file.fileName.removeSuffix(".disabled"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (file.enabled) obsi.text else obsi.textDim,
                )
                val requiredByLabel = stringResourceCompat(R.string.content_required_by, lockedBy.first())
                val subtitle = buildString {
                    append(file.entry?.versionNumber ?: file.kind)
                    if (!file.enabled) append(" · disabled")
                    if (lock) append(" · ").append(requiredByLabel)
                }
                Text(subtitle, style = MaterialTheme.typography.labelMedium, color = if (lock) obsi.accent else obsi.textDim)
            }
            Switch(
                checked = file.enabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(checkedTrackColor = obsi.accent),
            )
            ObsiGhostButton(
                text = stringResourceCompat(R.string.versions_delete),
                onClick = { if (!lock) onDelete() else onShowInfo() },
                enabled = !lock, // grayed out while other installed mods need this file
            )
        }
    }
}

@Composable
private fun InstanceSettingsPane(instance: studio.obsifox.obsilauncher.core.instance.Instance) {
    val context = LocalContext.current
    val app = context.app
    val obsi = LocalObsi.current
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf(instance.name) }
    var memory by remember { mutableStateOf(if (instance.memoryMb > 0) instance.memoryMb else app.settings.memoryMbValue) }
    var useCustomMemory by remember { mutableStateOf(instance.memoryMb > 0) }
    var jvmArgs by remember { mutableStateOf(instance.javaArgs.ifEmpty { app.settings.javaArgsValue }) }
    var useCustomArgs by remember { mutableStateOf(instance.javaArgs.isNotEmpty()) }
    var progress by remember { mutableStateOf<InstallState?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        SectionTitle(stringResourceCompat(R.string.instance_custom))
        GlassCard {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResourceCompat(R.string.instance_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            ObsiButton(
                stringResourceCompat(R.string.ok),
                onClick = {
                    app.instances.rename(instance.id, name)
                    message = context.getString(R.string.instance_saved)
                },
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        SectionTitle(stringResourceCompat(R.string.instance_game))
        GlassCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResourceCompat(R.string.instance_custom_memory),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = useCustomMemory, onCheckedChange = { useCustomMemory = it })
            }
            if (useCustomMemory) {
                Text(
                    "${stringResourceCompat(R.string.settings_memory)}: $memory",
                    style = MaterialTheme.typography.bodyMedium,
                    color = obsi.textDim,
                )
                Slider(
                    value = memory.toFloat(),
                    onValueChange = { memory = ((it.toInt() / 256) * 256).coerceAtLeast(512) },
                    valueRange = 512f..8192f,
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResourceCompat(R.string.instance_custom_args),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = useCustomArgs, onCheckedChange = { useCustomArgs = it })
            }
            if (useCustomArgs) {
                OutlinedTextField(
                    value = jvmArgs,
                    onValueChange = { jvmArgs = it },
                    label = { Text(stringResourceCompat(R.string.settings_java_args)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            ObsiButton(
                stringResourceCompat(R.string.ok),
                onClick = {
                    app.instances.update(instance.id) {
                        it.copy(
                            memoryMb = if (useCustomMemory) memory else 0,
                            javaArgs = if (useCustomArgs) jvmArgs else "",
                        )
                    }
                    message = context.getString(R.string.instance_saved)
                },
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        SectionTitle(stringResourceCompat(R.string.instance_files))
        GlassCard {
            Text(
                stringResourceCompat(R.string.instance_repair_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = obsi.textDim,
            )
            if (progress is InstallState.Running) {
                ProgressRow(
                    when ((progress as InstallState.Running).step) {
                        "verify" -> stringResourceCompat(R.string.dl_step_verify)
                        "repair_client" -> stringResourceCompat(R.string.dl_step_repair_client)
                        "repair_libraries" -> stringResourceCompat(R.string.dl_step_repair_libraries)
                        else -> (progress as InstallState.Running).step
                    },
                    (progress as InstallState.Running).fraction,
                    Modifier.padding(top = 8.dp),
                )
            } else {
                ObsiButton(
                    stringResourceCompat(R.string.instance_repair),
                    onClick = {
                        progress = null
                        scope.launch {
                            app.installer.repair(instance.versionId, app.settings) { progress = it }
                            message = context.getString(R.string.instance_repair_done)
                            progress = null
                        }
                    },
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            message?.let {
                Text(it, color = obsi.accent, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}
