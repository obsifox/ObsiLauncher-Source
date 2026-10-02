package studio.obsifox.launcher.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.launch
import studio.obsifox.launcher.core.instance.Instance
import studio.obsifox.launcher.core.instance.LoaderType
import studio.obsifox.launcher.core.launch.LaunchBuilder
import studio.obsifox.launcher.core.modrinth.ContentFile
import studio.obsifox.launcher.core.modrinth.ContentUpdate
import studio.obsifox.launcher.core.modrinth.ProjectKind
import studio.obsifox.launcher.core.util.Platform

@Composable
fun InstanceDetailScreen(id: String, initialTab: Int) {
    val app = LocalApp.current
    val instances by app.core.instances.flow.collectAsState()
    val sessions by app.sessions.collectAsState()
    val inst = instances.firstOrNull { it.id == id }
    var tab by remember(id) { mutableIntStateOf(initialTab) }
    if (inst == null) {
        EmptyState(ObsiIcons.Apps, t("empty_instances")) { SoftButton(t("back"), { app.go(Screen.Instances) }, icon = ObsiIcons.ArrowBack) }
        return
    }
    val running = sessions[inst.id]?.running?.value == true
    Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            IconAction(ObsiIcons.ArrowBack, t("back"), { app.go(Screen.Instances) }, tint = obsi().text)
            InstanceIcon(inst, 52.dp)
            Column(Modifier.weight(1f)) {
                Text(inst.name, style = MaterialTheme.typography.titleLarge, color = obsi().text, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Text(inst.subtitle, color = obsi().accent)
            }
            SoftButton(t("open_folder"), { SystemOpen.open(app.core.paths.gameDir(inst.id)) }, icon = ObsiIcons.Folder)
            if (running) SoftButton(t("stop"), { app.stop(inst.id) }, icon = ObsiIcons.Stop, danger = true)
            else PrimaryButton(t("play"), { app.play(inst) }, icon = ObsiIcons.Play)
        }
        val titles = listOf(t("tab_mods"), t("tab_resourcepacks"), t("tab_shaders"), t("tab_settings"))
        TabRow(selectedTabIndex = tab, containerColor = Color.Transparent, contentColor = obsi().accent) {
            titles.forEachIndexed { i, title -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(title) }, selectedContentColor = obsi().accent, unselectedContentColor = obsi().textDim) }
        }
        when (tab) {
            0 -> ContentTab(inst, ProjectKind.MOD)
            1 -> ContentTab(inst, ProjectKind.RESOURCEPACK)
            2 -> ContentTab(inst, ProjectKind.SHADER)
            else -> SettingsTab(inst)
        }
    }
}

@Composable
private fun ContentTab(inst: Instance, kind: ProjectKind) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    var files by remember(inst.id, kind) { mutableStateOf<List<ContentFile>?>(null) }
    var updates by remember(inst.id, kind) { mutableStateOf<List<ContentUpdate>>(emptyList()) }
    var checked by remember(inst.id, kind) { mutableStateOf(false) }
    var busy by remember(inst.id, kind) { mutableStateOf(false) }
    val tasks by app.tasks.collectAsState()
    val activeTasks = tasks.count { it.status == TaskStatus.RUNNING }

    fun reload() { scope.launch { files = try { app.core.content.list(inst, kind) } catch (_: Exception) { emptyList() } } }
    LaunchedEffect(inst.id, kind, activeTasks) { reload() }

    val vanillaMods = kind == ProjectKind.MOD && inst.loader == LoaderType.VANILLA
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            PrimaryButton(t("add_from_modrinth"), { app.go(Screen.Browse(inst.id, kind)) }, icon = ObsiIcons.Add, enabled = !vanillaMods)
            SoftButton(t("check_updates"), {
                scope.launch {
                    busy = true
                    try {
                        app.core.content.identifyUnknown(inst)
                        updates = app.core.content.checkUpdates(inst, kind)
                        checked = true
                        reload()
                    } catch (e: Exception) {
                        app.toast(e.message ?: app.s["error"])
                    } finally { busy = false }
                }
            }, icon = ObsiIcons.Refresh, enabled = !busy && files?.isNotEmpty() == true)
            if (updates.isNotEmpty()) {
                SoftButton(t("update_all"), {
                    val list = updates
                    updates = emptyList()
                    app.task<Unit>(app.s.fmt("installing", inst.name)) { progress -> list.forEach { app.core.content.applyUpdate(inst, it, progress) } }
                }, icon = ObsiIcons.Download)
            }
            if (busy) Dim(t("loading"))
            else if (checked) Dim(if (updates.isEmpty()) t("no_updates") else tf("updates_found", updates.size))
        }
        if (vanillaMods) Text(t("loader_none_hint"), color = obsi().warn, fontSize = 13.sp)

        val list = files
        when {
            list == null -> Dim(t("loading"))
            list.isEmpty() -> EmptyState(ObsiIcons.Extension, t("no_content"))
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(list, key = { it.kind.name + it.fileName }) { f ->
                    val update = updates.firstOrNull { it.file.fileName == f.fileName }
                    ContentRow(inst, f, update, onChanged = { reload() })
                }
            }
        }
    }
}

@Composable
private fun ContentRow(inst: Instance, f: ContentFile, update: ContentUpdate?, onChanged: () -> Unit) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    ObsiCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            RemoteImage(f.entry?.iconUrl, f.displayName, 40.dp)
            Column(Modifier.weight(1f)) {
                Text(f.displayName, color = if (f.enabled) obsi().text else obsi().textDim, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                Dim((f.entry?.versionNumber?.let { "$it · " } ?: "") + formatSize(f.sizeBytes) + (if (f.entry == null) " · " + t("unknown_source") else ""))
            }
            if (update != null) {
                Pill(update.latest.versionNumber, obsi().good)
                SoftButton(t("update"), { app.task<Unit>(app.s.fmt("installing", f.displayName)) { p -> app.core.content.applyUpdate(inst, update, p) } })
            }
            ObsiSwitch(f.enabled, { on -> scope.launch { app.core.content.setEnabled(inst, f, on); onChanged() } })
            IconAction(ObsiIcons.Delete, t("remove"), { scope.launch { app.core.content.remove(inst, f); onChanged() } }, tint = obsi().bad)
        }
    }
}

@Composable
private fun SettingsTab(inst: Instance) {
    val app = LocalApp.current
    val settings by app.core.settings.flow.collectAsState()
    var name by remember(inst.id) { mutableStateOf(inst.name) }
    var java by remember(inst.id) { mutableStateOf(inst.javaPath.orEmpty()) }
    val autoMax = remember(settings.defaultMaxMemoryMb) { LaunchBuilder.effectiveMaxMemoryMb(inst.copy(maxMemoryMb = null), settings) }
    var maxMb by remember(inst.id) { mutableStateOf((inst.maxMemoryMb ?: autoMax).toFloat()) }
    var jvm by remember(inst.id) { mutableStateOf(inst.jvmArgs) }
    var gameArgs by remember(inst.id) { mutableStateOf(inst.gameArgs) }
    var width by remember(inst.id) { mutableStateOf(inst.width?.toString().orEmpty()) }
    var height by remember(inst.id) { mutableStateOf(inst.height?.toString().orEmpty()) }
    var fullscreen by remember(inst.id) { mutableStateOf(inst.fullscreen) }
    var join by remember(inst.id) { mutableStateOf(inst.autoJoinServer.orEmpty()) }
    var notes by remember(inst.id) { mutableStateOf(inst.notes) }
    val sysMb = remember { Platform.totalMemoryMb().takeIf { it > 0 } ?: 16384 }
    val maxSlider = (sysMb * 3 / 4).coerceAtLeast(2048).toFloat()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ObsiCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LabeledField(t("instance_name"), name, { name = it })
                Text("${t("memory_max")}: ${maxMb.toInt()} MB", color = obsi().text)
                Slider(maxMb, { maxMb = (it / 256f).toInt() * 256f }, valueRange = 512f..maxSlider,
                    colors = SliderDefaults.colors(thumbColor = obsi().accent, activeTrackColor = obsi().accent))
                LabeledField(t("java_path"), java, { java = it }, hint = t("java_auto"))
                LabeledField(t("jvm_args"), jvm, { jvm = it }, hint = "-XX:+UseZGC -Dkey=value")
                LabeledField(t("game_args"), gameArgs, { gameArgs = it })
            }
        }
        ObsiCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(t("resolution"), color = obsi().text, fontWeight = FontWeight.Medium)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LabeledField(t("width"), width, { width = it.filter(Char::isDigit).take(5) }, Modifier.weight(1f), hint = "854")
                    LabeledField(t("height"), height, { height = it.filter(Char::isDigit).take(5) }, Modifier.weight(1f), hint = "480")
                }
                SwitchRow(t("fullscreen"), fullscreen, { fullscreen = it })
                LabeledField(t("auto_join"), join, { join = it }, hint = t("auto_join_hint"))
                LabeledField(t("notes"), notes, { notes = it }, singleLine = false, minLines = 3)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryButton(t("save"), {
                val updated = inst.copy(
                    name = name.trim().ifBlank { inst.name },
                    javaPath = java.trim().ifBlank { null },
                    maxMemoryMb = maxMb.toInt().takeIf { it != autoMax || inst.maxMemoryMb != null },
                    jvmArgs = jvm.trim(), gameArgs = gameArgs.trim(),
                    width = width.toIntOrNull()?.takeIf { it > 0 && height.toIntOrNull() != null }, height = height.toIntOrNull()?.takeIf { it > 0 && width.toIntOrNull() != null },
                    fullscreen = fullscreen, autoJoinServer = join.trim().ifBlank { null }, notes = notes,
                )
                app.core.instances.save(updated)
                app.toast(app.s["saved"])
            }, icon = ObsiIcons.Check)
        }
    }
}
