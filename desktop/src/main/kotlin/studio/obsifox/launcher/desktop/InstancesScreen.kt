package studio.obsifox.launcher.desktop

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import studio.obsifox.launcher.core.instance.Instance
import studio.obsifox.launcher.core.instance.LoaderType
import studio.obsifox.launcher.core.loaders.LoaderVersion
import studio.obsifox.launcher.core.mojang.ManifestEntry

@Composable
fun InstancesScreen() {
    val app = LocalApp.current
    val instances by app.core.instances.flow.collectAsState()
    val sessions by app.sessions.collectAsState()
    var showNew by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Instance?>(null) }
    val importTitle = t("import_mrpack")

    Column(Modifier.fillMaxSize().padding(28.dp)) {
        SectionHeader(t("instances_title")) {
            SoftButton(t("import_mrpack"), {
                pickFile(importTitle, "mrpack")?.let { app.importMrpack(it) }
            }, icon = ObsiIcons.Download)
            HSpace(10)
            PrimaryButton(t("new_instance"), { showNew = true }, icon = ObsiIcons.Add)
        }
        VSpace(8)
        if (instances.isEmpty()) {
            EmptyState(ObsiIcons.Apps, t("empty_instances")) { PrimaryButton(t("create_instance"), { showNew = true }, icon = ObsiIcons.Add) }
        } else {
            LazyVerticalGrid(GridCells.Adaptive(320.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                items(instances, key = { it.id }) { inst ->
                    InstanceCard(inst, running = sessions[inst.id]?.running?.value == true, onDelete = { deleting = inst })
                }
            }
        }
    }
    if (showNew) NewInstanceDialog(onDismiss = { showNew = false })
    deleting?.let { inst ->
        ConfirmDialog(t("delete_title"), tf("delete_text", inst.name), t("delete"), onConfirm = { app.deleteInstance(inst.id) }, onDismiss = { deleting = null }, danger = true)
    }
}

@Composable
private fun InstanceCard(inst: Instance, running: Boolean, onDelete: () -> Unit) {
    val app = LocalApp.current
    var menu by remember { mutableStateOf(false) }
    ObsiCard(Modifier.fillMaxWidth(), onClick = { app.go(Screen.InstanceDetail(inst.id)) }) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                InstanceIcon(inst, 56.dp)
                Column(Modifier.weight(1f)) {
                    Text(inst.name, style = MaterialTheme.typography.titleMedium, color = Obsi.text, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    Text(inst.subtitle, color = Obsi.orange, fontSize = 13.sp)
                    Dim(lastPlayedText(inst))
                }
                Box {
                    IconAction(ObsiIcons.Edit, t("profile_settings"), { menu = true })
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem(text = { Text(t("profile_settings")) }, onClick = { menu = false; app.go(Screen.InstanceDetail(inst.id, 3)) })
                        DropdownMenuItem(text = { Text(t("open_folder")) }, onClick = { menu = false; SystemOpen.open(app.core.paths.gameDir(inst.id)) })
                        DropdownMenuItem(text = { Text(t("duplicate")) }, onClick = { menu = false; app.duplicateInstance(inst) })
                        DropdownMenuItem(text = { Text(t("delete"), color = Obsi.red) }, onClick = { menu = false; onDelete() })
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (running) {
                    SoftButton(t("stop"), { app.stop(inst.id) }, icon = ObsiIcons.Stop, danger = true)
                    SoftButton(t("nav_console"), { app.go(Screen.Console) }, icon = ObsiIcons.Terminal)
                } else {
                    PrimaryButton(t("play"), { app.play(inst) }, icon = ObsiIcons.Play)
                    SoftButton(t("add_from_modrinth"), { app.go(Screen.Browse(inst.id)) }, icon = ObsiIcons.Extension)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------------

@Composable
fun NewInstanceDialog(onDismiss: () -> Unit) {
    val app = LocalApp.current
    var name by remember { mutableStateOf("") }
    var nameTouched by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf("") }
    var snapshots by remember { mutableStateOf(false) }
    var mc by remember { mutableStateOf<String?>(null) }
    var loader by remember { mutableStateOf(LoaderType.VANILLA) }
    var loaderVersion by remember { mutableStateOf<String?>(null) }

    val manifest by produceState<List<ManifestEntry>?>(null) {
        value = try { app.core.mojang.manifest().versions } catch (_: Exception) { emptyList() }
    }
    val latestRelease = remember(manifest) { manifest?.firstOrNull { it.type == "release" }?.id }
    LaunchedEffect(latestRelease) { if (mc == null) mc = latestRelease }

    val loaderVersions by produceState<List<LoaderVersion>?>(null, loader, mc) {
        value = null
        val v = mc
        value = if (loader == LoaderType.VANILLA || v == null) emptyList() else try { app.core.loaders.versions(loader, v) } catch (_: Exception) { emptyList() }
        loaderVersion = value?.let { l -> (l.firstOrNull { it.recommended } ?: l.firstOrNull { it.stable } ?: l.firstOrNull())?.version }
    }

    // default name follows the selection until the user types their own
    LaunchedEffect(mc, loader) {
        if (!nameTouched && mc != null) name = if (loader == LoaderType.VANILLA) "Minecraft $mc" else "${loader.display} $mc"
    }

    val visible = remember(manifest, filter, snapshots) {
        manifest.orEmpty().filter { (snapshots || it.type == "release") && (it.type == "release" || it.type == "snapshot") && it.id.contains(filter.trim(), true) }
    }
    val noLoader = loader != LoaderType.VANILLA && loaderVersions?.isEmpty() == true

    WideDialog(
        t("new_instance"), onDismiss, width = 620.dp,
        confirm = {
            PrimaryButton(t("create"), {
                app.createInstance(name, mc!!, loader, loaderVersion)
                onDismiss()
            }, enabled = mc != null && name.isNotBlank() && !noLoader && (loader == LoaderType.VANILLA || loaderVersion != null))
        },
    ) {
        LabeledField(t("instance_name"), name, { name = it; nameTouched = true })

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(t("mc_version"), color = Obsi.textDim, modifier = Modifier.weight(1f))
            Checkbox(snapshots, { snapshots = it })
            Text(t("show_snapshots"), color = Obsi.textDim)
        }
        LabeledField(t("version_filter"), filter, { filter = it })
        if (manifest == null) {
            Dim(t("loading"))
        } else {
            LazyColumn(Modifier.height(150.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Obsi.bg1)) {
                items(visible.take(200), key = { it.id }) { v ->
                    Row(
                        Modifier.fillMaxWidth().clickable { mc = v.id }.background(if (v.id == mc) Obsi.bg3 else Color.Transparent).padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(v.id, color = if (v.id == mc) Obsi.orange else Obsi.text, fontWeight = if (v.id == mc) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(1f))
                        if (v.id == latestRelease) Pill(t("latest"), Obsi.green)
                        else if (v.type == "snapshot") Pill("snapshot", Obsi.yellow)
                    }
                }
            }
        }

        Text(t("loader"), color = Obsi.textDim)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LoaderType.entries.forEach { l ->
                FilterChip(selected = loader == l, onClick = { loader = l }, label = { Text(l.display) })
            }
        }
        if (loader != LoaderType.VANILLA) {
            when {
                loaderVersions == null -> Dim(t("loading"))
                loaderVersions!!.isEmpty() -> Text(tf("no_compatible", "$mc ${loader.display}"), color = Obsi.red)
                else -> DropdownField(
                    t("loader_version"),
                    loaderVersions!!.firstOrNull { it.version == loaderVersion }?.let { it.version + if (it.recommended) " · " + t("recommended") else "" } ?: "",
                    loaderVersions!!.take(60).map { it.version to (it.version + (if (it.recommended) " · " + t("recommended") else "") + (if (!it.stable) " (beta)" else "")) },
                    { loaderVersion = it },
                )
            }
        }
    }
}
