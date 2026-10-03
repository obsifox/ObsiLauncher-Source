package studio.obsifox.obsilauncher.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import studio.obsifox.obsilauncher.R
import studio.obsifox.obsilauncher.app
import studio.obsifox.obsilauncher.core.game.GameState
import studio.obsifox.obsilauncher.core.instance.Instance
import studio.obsifox.obsilauncher.ui.components.ObsiGhostButton
import studio.obsifox.obsilauncher.ui.theme.LocalObsi

/**
 * Home = the "Play" tab. Full-bleed wallpaper, a compact info bar
 * (version · playtime · last played) and one big PLAY button — like a
 * console dashboard instead of a stack of cards.
 */
@Composable
fun HomeScreen(
    onPlay: () -> Unit,
    onPickVersion: () -> Unit,
    onPickAccount: () -> Unit,
    onOpenConsole: () -> Unit,
    onOpenInstance: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
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
    val canPlay = hasVersion && account != null && pack != null &&
        gameState != GameState.RUNNING && gameState != GameState.PREPARING &&
        installing !is studio.obsifox.obsilauncher.core.game.InstallState.Running

    Box(Modifier.fillMaxSize()) {
        // empty state ----------------------------------------------------------
        if (instances.isEmpty()) {
            Column(
                Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    stringResourceCompat(R.string.home_no_instances),
                    color = obsi.text,
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(10.dp))
                ObsiGhostButton(
                    text = stringResourceCompat(R.string.versions_install),
                    onClick = onPickVersion,
                )
            }
            return@Box
        }

        // readability scrim over the wallpaper ----------------------------------
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(200.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color(0xB3140F0C)),
                    ),
                ),
        )

        // bottom dashboard -------------------------------------------------------
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
        ) {
            // status / warning line — only when needed
            when {
                gameState == GameState.RUNNING || gameState == GameState.PREPARING -> {
                    StatusChip(
                        text = if (gameState == GameState.RUNNING) {
                            stringResourceCompat(R.string.console_running)
                        } else {
                            stringResourceCompat(R.string.home_launching, active?.name ?: "")
                        },
                        accent = obsi.accent,
                        actionText = stringResourceCompat(R.string.console_title),
                        onAction = onOpenConsole,
                    )
                }
                !canPlay -> {
                    StatusChip(
                        text = when {
                            !hasVersion -> stringResourceCompat(R.string.home_no_version)
                            account == null -> stringResourceCompat(R.string.home_no_account)
                            pack == null -> stringResourceCompat(R.string.home_missing_runtime)
                            else -> ""
                        },
                        accent = Color(0xFFE5A24C),
                        actionText = when {
                            !hasVersion -> stringResourceCompat(R.string.versions_install)
                            account == null -> stringResourceCompat(R.string.accounts_add)
                            else -> stringResourceCompat(R.string.settings_runtime_install)
                        },
                        onAction = when {
                            !hasVersion -> onPickVersion
                            account == null -> onPickAccount
                            else -> onPickVersion
                        },
                    )
                }
            }
            if (gameState == GameState.RUNNING || gameState == GameState.PREPARING || !canPlay) {
                Spacer(Modifier.height(8.dp))
            }

            // instance pills — small, horizontal, tidy
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                instances.forEach { instance ->
                    InstancePill(
                        instance = instance,
                        selected = instance.id == activeId,
                        onSelect = {
                            app.instances.setActive(instance.id)
                            app.settings.selectedVersionValue = instance.versionId
                        },
                    )
                }
                ManagePill(onClick = onOpenInstance)
            }
            Spacer(Modifier.height(12.dp))

            // info chips + PLAY ---------------------------------------------------
            Row(verticalAlignment = Alignment.Bottom) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    modifier = Modifier.padding(bottom = 6.dp),
                ) {
                    // version chip → Versions screen
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable(onClick = onPickVersion),
                    ) {
                        GrassBlockIcon(Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        InfoText(active?.let { loaderBadge(it) } ?: "—")
                    }
                    // playtime
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ClockIcon(Modifier.size(17.dp), obsi.text)
                        Spacer(Modifier.width(8.dp))
                        InfoText(formatPlaytime(active?.playSeconds ?: 0))
                    }
                    // last played
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GamepadIcon(Modifier.size(19.dp), obsi.text)
                        Spacer(Modifier.width(8.dp))
                        InfoText(formatLastPlayed(active?.lastPlayed ?: 0))
                    }
                }
                Spacer(Modifier.weight(1f))
                PlayButton(
                    enabled = canPlay,
                    subtitle = active?.let { loaderBadge(it) } ?: "",
                    onClick = onPlay,
                )
            }
        }
    }
}

/** The big bottom-right PLAY button from the reference design. */
@Composable
private fun PlayButton(enabled: Boolean, subtitle: String, onClick: () -> Unit) {
    // clearly gray while disabled — the version must be installed first
    val container = if (enabled) LocalObsi.current.accent else Color(0x64888888)
    val contentColor = if (enabled) Color(0xFF17110B) else Color(0xFFDDDDDD)
    Column(
        Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(container)
            .clickable(enabled = enabled, onClick = onClick)
            .widthIn(min = 210.dp)
            .padding(horizontal = 30.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResourceCompat(R.string.home_play_big),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black,
            letterSpacing = 3.sp,
            color = contentColor,
        )
        if (subtitle.isNotBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = contentColor.copy(alpha = 0.75f),
            )
        }
    }
}

/** small pill for transient status or a missing-prerequisite warning. */
@Composable
private fun StatusChip(text: String, accent: Color, actionText: String, onAction: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color(0x99140F0C))
            .border(1.dp, Color(0x24FFFFFF), RoundedCornerShape(999.dp))
            .padding(start = 12.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(accent),
        )
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = Color(0xFFF4EFEA))
        Spacer(Modifier.width(8.dp))
        Text(
            text = actionText,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = accent,
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .clickable(onClick = onAction)
                .padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun InstancePill(instance: Instance, selected: Boolean, onSelect: () -> Unit) {
    val obsi = LocalObsi.current
    Row(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color(0x80140F0C))
            .border(
                1.dp,
                if (selected) obsi.accent.copy(alpha = 0.85f) else Color(0x24FFFFFF),
                RoundedCornerShape(999.dp),
            )
            .clickable(onClick = onSelect)
            .padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = instance.name,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) obsi.accent else obsi.text,
        )
    }
}

@Composable
private fun ManagePill(onClick: () -> Unit) {
    Text(
        text = stringResourceCompat(R.string.instance_details),
        style = MaterialTheme.typography.labelMedium,
        color = Color(0xFFF4EFEA).copy(alpha = 0.75f),
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color(0x50140F0C))
            .border(1.dp, Color(0x24FFFFFF), RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 5.dp),
    )
}

@Composable
private fun InfoText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall.copy(
            shadow = Shadow(
                color = Color(0x99000000),
                offset = Offset(0f, 1f),
                blurRadius = 6f,
            ),
        ),
        color = Color(0xFFF4EFEA),
    )
}

// icons — drawn, not font glyphs, so they never break -------------------------

/** 8-bit grass block: green top, dirt body. */
@Composable
private fun GrassBlockIcon(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        drawRect(Color(0xFF7A4E2A), size = Size(w, h))
        // pixel specks in the dirt
        val p = w / 8f
        drawRect(Color(0xFF5E3A1E), topLeft = Offset(p, h * 0.55f), size = Size(p, p))
        drawRect(Color(0xFF93603A), topLeft = Offset(p * 4, h * 0.7f), size = Size(p, p))
        drawRect(Color(0xFF5E3A1E), topLeft = Offset(p * 6, h * 0.5f), size = Size(p, p))
        // grass cap
        drawRect(Color(0xFF6FAE3E), size = Size(w, h * 0.3f))
        drawRect(Color(0xFF8CCB54), topLeft = Offset(0f, 0f), size = Size(w, h * 0.12f))
        // rim
        drawRect(
            Color(0x33000000),
            size = Size(w, h),
            style = Stroke(width = 1.dp.toPx()),
        )
    }
}

/** thin clock face. */
@Composable
private fun ClockIcon(modifier: Modifier = Modifier, color: Color) {
    Canvas(modifier) {
        val stroke = 1.6.dp.toPx()
        val r = size.minDimension / 2f - stroke
        val c = Offset(size.width / 2f, size.height / 2f)
        drawCircle(color, radius = r, center = c, style = Stroke(stroke))
        drawLine(
            color,
            start = c,
            end = Offset(c.x, c.y - r * 0.62f),
            strokeWidth = stroke,
        )
        drawLine(
            color,
            start = c,
            end = Offset(c.x + r * 0.5f, c.y + r * 0.18f),
            strokeWidth = stroke,
        )
    }
}

/** rounded gamepad with a d-pad cross and two buttons. */
@Composable
private fun GamepadIcon(modifier: Modifier = Modifier, color: Color) {
    Canvas(modifier) {
        val stroke = 1.6.dp.toPx()
        val w = size.width
        val h = size.height
        drawRoundRect(
            color,
            size = Size(w, h * 0.62f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(h * 0.3f),
            style = Stroke(stroke),
        )
        // d-pad cross (left)
        val cx = w * 0.26f
        val cy = h * 0.31f
        val arm = w * 0.09f
        drawLine(color, Offset(cx - arm, cy), Offset(cx + arm, cy), stroke)
        drawLine(color, Offset(cx, cy - arm), Offset(cx, cy + arm), stroke)
        // buttons (right)
        drawCircle(color, radius = stroke * 0.75f, center = Offset(w * 0.68f, cy - arm * 0.5f))
        drawCircle(color, radius = stroke * 0.75f, center = Offset(w * 0.76f, cy + arm * 0.5f))
    }
}

// formatting -------------------------------------------------------------------

@Composable
internal fun formatPlaytime(seconds: Long): String {
    if (seconds <= 0) return "—"
    return when {
        seconds < 3600 -> stringResourceCompat(R.string.time_min, (seconds / 60).coerceAtLeast(1))
        seconds < 48 * 3600L -> stringResourceCompat(R.string.time_hour, seconds / 3600)
        else -> stringResourceCompat(R.string.time_day, seconds / 86_400)
    }
}

@Composable
internal fun formatLastPlayed(epochMs: Long): String {
    if (epochMs <= 0) return "—"
    val diffMin = (System.currentTimeMillis() - epochMs) / 60_000
    return when {
        diffMin < 2 -> stringResourceCompat(R.string.last_now)
        diffMin < 60 -> stringResourceCompat(R.string.last_min_ago, diffMin)
        diffMin < 48 * 60L -> stringResourceCompat(R.string.last_hour_ago, diffMin / 60)
        else -> stringResourceCompat(R.string.last_day_ago, diffMin / 1440)
    }
}

internal fun loaderBadge(instance: Instance): String = when (instance.loaderType.name) {
    "VANILLA" -> instance.mcVersion
    else -> "${instance.mcVersion} · ${instance.loaderType.display}" +
        (instance.loaderVersion?.let { " $it" } ?: "")
}
