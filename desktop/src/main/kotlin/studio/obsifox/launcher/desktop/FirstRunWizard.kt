package studio.obsifox.launcher.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import studio.obsifox.launcher.core.auth.AuthService

/**
 * First-run setup, opened automatically once - after the update gate - and never again.
 * Later changes happen in Settings; dismissing marks the setup as completed (drafts are thrown away).
 */
@Composable
fun FirstRunWizard() {
    val app = LocalApp.current
    val settings by app.core.settings.flow.collectAsState()
    val backdrop by app.backdrop.collectAsState()
    val accounts by app.core.accounts.flow.collectAsState()

    var step by remember { mutableIntStateOf(1) }
    var dTheme by remember { mutableStateOf(ThemeId.of(settings.theme)) }
    var dMode by remember { mutableStateOf(settings.wallpaperMode) }
    var dAdapt by remember { mutableStateOf(settings.adaptColors) }

    // live preview of the draft theme / accent while the wizard is open
    LaunchedEffect(dTheme, dAdapt, backdrop.palette) { Obsi.apply(dTheme, if (dAdapt) backdrop.palette else null) }

    fun close(commit: Boolean) {
        app.wizardOpen.value = false
        if (commit) app.core.settings.update { it.copy(theme = dTheme.key, wallpaperMode = dMode, adaptColors = dAdapt, firstRunComplete = true) }
        else app.core.settings.update { it.copy(firstRunComplete = true) }
        val st = app.core.settings.value
        Obsi.apply(ThemeId.of(st.theme), if (st.adaptColors) backdrop.palette else null)
    }

    Box(Modifier.fillMaxSize().background(Color(0xA6070505)), contentAlignment = Alignment.Center) {
        GlassPanel(strong = true, radius = 23.dp) {
            Row(Modifier.width(900.dp).height(560.dp)) {
                // ------------------------------------------------ sidebar
                Column(Modifier.width(240.dp).fillMaxHeight().background(Color.Black.copy(alpha = 0.13f)).padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FoxMark(30.dp)
                        Column {
                            Text("ObsiLauncher", color = Obsi.text, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            Text(t("wiz_first"), color = Obsi.textDim, fontSize = 8.sp, letterSpacing = 1.sp)
                        }
                    }
                    Text(t("wiz_steps"), color = Obsi.textDim, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp, modifier = Modifier.padding(top = 26.dp, bottom = 10.dp))
                    listOf(1 to t("wiz_step_theme"), 2 to t("wiz_step_bg"), 3 to t("wiz_step_acc")).forEach { (i, label) ->
                        val active = step == i
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(11.dp))
                                .background(if (active) Obsi.orange.copy(alpha = 0.14f) else Color.Transparent)
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp),
                        ) {
                            Box(
                                Modifier.size(23.dp).clip(RoundedCornerShape(8.dp))
                                    .border(1.dp, if (active) Obsi.orange.copy(alpha = 0.5f) else Obsi.soft, RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center,
                            ) { Text("0$i", color = if (active) Obsi.accentLight else Obsi.textDim, fontSize = 9.sp, fontWeight = FontWeight.Bold) }
                            Text(label, color = if (active) Obsi.text else Obsi.textDim, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Box(Modifier.weight(1f))
                    Dim(t("wiz_hint"), size = 9, maxLines = 5)
                }
                // ------------------------------------------------ content
                Column(Modifier.weight(1f).fillMaxHeight().padding(26.dp)) {
                    Row(verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(t("wiz_kicker"), color = Obsi.accentLight, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                            Text(when (step) { 1 -> t("wiz_t1"); 2 -> t("wiz_t2"); else -> t("wiz_t3") }, color = Obsi.text, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                            Dim(when (step) { 1 -> t("wiz_d1"); 2 -> t("wiz_d2"); else -> t("wiz_d3") }, size = 10, maxLines = 2)
                        }
                        Box(Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).clickable { close(false) }, contentAlignment = Alignment.Center) {
                            Icon(ObsiIcons.Close, t("close"), tint = Obsi.textDim, modifier = Modifier.size(14.dp))
                        }
                    }
                    val pickTitle = t("bg_pick")
                    Box(Modifier.weight(1f).padding(top = 18.dp)) {
                        when (step) {
                            1 -> Row(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                                WizardThemeCard(ThemeId.VANILLA, t("theme_vanilla"), t("theme_vanilla_desc"), listOf(Color(0xFF321B18), Color(0xFF6D341F), Color(0xFFE06B31)), dTheme == ThemeId.VANILLA, { dTheme = it }, Modifier.weight(1f))
                                WizardThemeCard(ThemeId.WHITE, t("theme_white"), t("theme_white_desc"), listOf(Color(0xFFF3EEE7), Color(0xFFFFAF5), Color(0xFFE2A47E)), dTheme == ThemeId.WHITE, { dTheme = it }, Modifier.weight(1f))
                                WizardThemeCard(ThemeId.BLACK, t("theme_black"), t("theme_black_desc"), listOf(Color(0xFF070809), Color(0xFF161719), Color(0xFFB55A2D)), dTheme == ThemeId.BLACK, { dTheme = it }, Modifier.weight(1f))
                            }
                            2 -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    ModeCard(ObsiIcons.Cube, t("bg_version"), t("bg_version_desc"), dMode == "version", { dMode = "version" }, Modifier.weight(1f))
                                    ModeCard(ObsiIcons.Sparkle, t("bg_latest"), t("bg_latest_desc"), dMode == "latest", { dMode = "latest" }, Modifier.weight(1f))
                                    ModeCard(ObsiIcons.Image, t("bg_custom"), t("bg_custom_desc"), dMode == "custom", {
                                        val f = pickImageFile(pickTitle)
                                        if (f != null) {
                                            app.task(pickTitle) { _ -> app.core.wallpapers.importCustom(f) }
                                            dMode = "custom"
                                        }
                                    }, Modifier.weight(1f))
                                }
                                // preview of the art the launcher would show right now
                                val bmp = backdrop.file?.let { remember(it) { loadBitmap(it) } }
                                Box(Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(14.dp)).background(Color.Black.copy(alpha = 0.25f))) {
                                    if (bmp != null) Image(bmp, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                    Text(backdrop.title ?: "", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.BottomStart).padding(12.dp))
                                }
                                SwitchRow(t("adapt_colors"), dAdapt, { dAdapt = it }, sub = t("adapt_colors_desc"))
                                Dim(t("bg_version_old") + "  ·  " + t("video_note"), size = 9, maxLines = 3)
                            }
                            3 -> WizardAccounts(app, accounts)
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(tf("wiz_step_of", step), color = Obsi.textDim, fontSize = 9.sp)
                        Box(Modifier.weight(1f))
                        if (step > 1) SoftButton(t("back"), { step-- })
                        if (step < 3) PrimaryButton(t("continue"), { step++ })
                        if (step == 3) PrimaryButton(t("wiz_finish"), { close(true) })
                    }
                }
            }
        }
    }
}

@Composable
private fun WizardThemeCard(id: ThemeId, name: String, desc: String, swatch: List<Color>, selected: Boolean, onPick: (ThemeId) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(15.dp))
            .border(1.dp, if (selected) Obsi.orange else Obsi.soft, RoundedCornerShape(15.dp))
            .background(if (selected) Obsi.orange.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.03f))
            .clickable { onPick(id) }.padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Box(Modifier.fillMaxWidth().height(64.dp).clip(RoundedCornerShape(10.dp)).background(Brush.linearGradient(swatch)))
        Text(name, color = Obsi.text, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Dim(desc, size = 8, maxLines = 2)
    }
}

@Composable
private fun ModeCard(icon: androidx.compose.ui.graphics.vector.ImageVector, name: String, desc: String, selected: Boolean, onPick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(13.dp))
            .border(1.dp, if (selected) Obsi.orange else Obsi.soft, RoundedCornerShape(13.dp))
            .background(if (selected) Obsi.orange.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.03f))
            .clickable(onClick = onPick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Box(Modifier.size(32.dp).clip(RoundedCornerShape(9.dp)).background(Color.Black.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Obsi.accentLight, modifier = Modifier.size(16.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(name, color = Obsi.text, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Dim(desc, size = 8, maxLines = 2)
        }
    }
}

@Composable
private fun WizardAccounts(app: AppController, accounts: List<studio.obsifox.launcher.core.auth.Account>) {
    var name by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val valid = AuthService.isValidOfflineName(name.trim())
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (accounts.isEmpty()) Dim(t("account_empty"), maxLines = 2)
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            accounts.forEach { a ->
                val active = a.id == (app.core.settings.value.selectedAccountId ?: accounts.first().id)
                Row(
                    Modifier.clip(RoundedCornerShape(8.dp))
                        .border(1.dp, if (active) Obsi.orange else Obsi.line, RoundedCornerShape(8.dp))
                        .background(if (active) Obsi.orange.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.11f))
                        .clickable { app.selectAccount(a.id) }.padding(horizontal = 9.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    LetterAvatar(a.username, 16.dp)
                    Text(a.username, color = Obsi.text, fontSize = 9.sp)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) {
                LabeledField(t("offline_name"), name, { name = it; error = null }, hint = t("offline_hint"))
            }
            SoftButton(t("add_offline"), {
                runCatching { app.core.auth.addOffline(name) }
                    .onSuccess { app.selectAccount(it.id); name = "" }
                    .onFailure { error = it.message }
            }, enabled = valid)
        }
        error?.let { Text(it, color = Obsi.red, fontSize = 9.sp) }
        Dim(t("local_only_note"), size = 9, maxLines = 3)
    }
}
