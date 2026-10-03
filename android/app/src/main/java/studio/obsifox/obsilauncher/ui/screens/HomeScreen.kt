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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import studio.obsifox.obsilauncher.R
import studio.obsifox.obsilauncher.app
import studio.obsifox.obsilauncher.core.game.GameState
import studio.obsifox.obsilauncher.core.instance.Instance
import studio.obsifox.obsilauncher.core.loaders.LoaderType
import studio.obsifox.obsilauncher.ui.components.LoaderIcon
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
    onDownload: () -> Unit,
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
    val loaderInstalling by app.loaders.state.collectAsState()
    val selectedVersion by app.settings.selectedVersion.collectAsState()
    val videoActive by app.wallpaper.active.collectAsState()
    val videoMuted by app.settings.videoMuted.collectAsState()

    val active = instances.firstOrNull { it.id == activeId }
    val account = accounts.firstOrNull { it.id == activeAccountId }
    val pack = packs.firstOrNull { it.name == runtimePack } ?: packs.firstOrNull()
    val hasVersion = active != null && app.installer.isInstalled(active.versionId)

    // ---- the big button's three states (v1.9.0) ----------------------------
    // gray  = the selected version is not downloaded yet  -> "DOWNLOAD"
    // blue  = a download is running (fills from the language's start side)
    // green = downloaded and ready                        -> "PLAY"
    val runningInstall = installing as? studio.obsifox.obsilauncher.core.game.InstallState.Running
    val runningLoader = loaderInstalling as? studio.obsifox.obsilauncher.core.game.InstallState.Running
    val downloadProgress: Float? = when {
        runningInstall != null -> runningInstall.fraction
            ?: if (runningInstall.total > 0) runningInstall.done.toFloat() / runningInstall.total else 0f
        runningLoader != null -> runningLoader.fraction ?: 0.5f
        else -> null
    }
    val canDownload = selectedVersion.isNotBlank() && downloadProgress == null &&
        gameState != GameState.RUNNING && gameState != GameState.PREPARING
    val canPlay = hasVersion && account != null && pack != null &&
        gameState != GameState.RUNNING && gameState != GameState.PREPARING &&
        downloadProgress == null

    Box(Modifier.fillMaxSize()) {
        // v1.10.0 — sound toggle for the bundled background video; it sits
        // just under the floating top bar, on the end side, out of the way.
        // Rendered before the empty-state branch so it is always available.
        if (videoActive.isVideo) {
            val muteDesc = stringResourceCompat(if (videoMuted) R.string.bg_unmute else R.string.bg_mute)
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 76.dp, end = 16.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0x99140F0C))
                    .border(1.dp, Color(0x24FFFFFF), CircleShape)
                    .clickable { app.settings.videoMutedValue = !videoMuted }
                    .semantics { contentDescription = muteDesc },
                contentAlignment = Alignment.Center,
            ) {
                SpeakerIcon(
                    muted = videoMuted,
                    color = Color(0xFFF4EFEA),
                    modifier = Modifier.size(22.dp),
                )
            }
        }

        // empty state ----------------------------------------------------------
        if (instances.isEmpty()) {
            Column(
                Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    if (canDownload) {
                        stringResourceCompat(R.string.home_ready_to_download, selectedVersion)
                    } else {
                        stringResourceCompat(R.string.home_no_instances)
                    },
                    color = obsi.text,
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(10.dp))
                if (canDownload) {
                    PlayButton(
                        label = stringResourceCompat(R.string.home_download_big),
                        progress = null,
                        ready = false,
                        enabled = true,
                        subtitle = "",
                        onClick = onDownload,
                    )
                } else {
                    ObsiGhostButton(
                        text = stringResourceCompat(R.string.versions_install),
                        onClick = onPickVersion,
                    )
                }
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
                            !hasVersion -> stringResourceCompat(R.string.home_download_big)
                            account == null -> stringResourceCompat(R.string.accounts_add)
                            else -> stringResourceCompat(R.string.settings_runtime_install)
                        },
                        onAction = when {
                            !hasVersion -> onDownload
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
                    // version chip → Versions screen — grass block for vanilla,
                    // the loader's own mark for loader builds
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable(onClick = onPickVersion),
                    ) {
                        LoaderIcon(
                            type = active?.loaderType ?: LoaderType.VANILLA,
                            modifier = Modifier.size(20.dp),
                        )
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
                    label = when {
                        downloadProgress != null -> stringResourceCompat(R.string.home_download_big)
                        canPlay -> stringResourceCompat(R.string.home_play_big)
                        else -> stringResourceCompat(R.string.home_download_big)
                    },
                    progress = downloadProgress,
                    ready = canPlay,
                    enabled = canPlay || canDownload,
                    subtitle = active?.let { loaderBadge(it) } ?: selectedVersion,
                    onClick = { if (canPlay) onPlay() else if (canDownload) onDownload() },
                )
            }
        }
    }
}

/**
 * The big bottom-right button — PLAY when ready, DOWNLOAD otherwise.
 * While a download runs the button fills with blue from the language's
 * start side (right for Farsi, left for English) and turns fully green
 * the moment the game is ready.
 */
@Composable
private fun PlayButton(
    label: String,
    progress: Float?,
    ready: Boolean,
    enabled: Boolean,
    subtitle: String,
    onClick: () -> Unit,
) {
    val obsi = LocalObsi.current
    val container = when {
        progress != null -> Color(0xFF2E2E32)             // dark base under the blue fill
        ready -> obsi.accent                              // Minecraft green
        enabled -> Color(0xFF6E6E72)                      // gray: not downloaded
        else -> Color(0xFF49494D)
    }
    val contentColor = if (ready || enabled || progress != null) Color.White else Color(0xFFC9C9CC)
    Box(
        Modifier
            .widthIn(min = 210.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(container)
            .border(1.dp, Color(0xFF17181A), RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        // the fill: grows from the START corner — right side in Farsi (RTL),
        // left side in English (LTR) — and switches green when complete
        if (progress != null) {
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .background(if (progress >= 1f) obsi.accent else Color(0xFF2F6FBE)),
            )
        }
        Column(
            Modifier.padding(horizontal = 30.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                letterSpacing = 3.sp,
                color = contentColor,
            )
            if (progress != null) {
                Text(
                    text = "${(progress.coerceIn(0f, 1f) * 100).toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = contentColor,
                )
            } else if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = contentColor.copy(alpha = 0.8f),
                    maxLines = 1,
                )
            }
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
        // every pill carries its mark: grass block for vanilla builds,
        // the loader's own icon for loader builds
        LoaderIcon(
            type = instance.loaderType,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(6.dp))
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

/** speaker with sound waves — crossed out when the video is muted. */
@Composable
private fun SpeakerIcon(muted: Boolean, modifier: Modifier = Modifier, color: Color) {
    Canvas(modifier) {
        val stroke = 1.7.dp.toPx()
        val w = size.width
        val h = size.height
        // speaker body: box + cone
        val body = Path().apply {
            moveTo(w * 0.18f, h * 0.40f)
            lineTo(w * 0.40f, h * 0.40f)
            lineTo(w * 0.58f, h * 0.24f)
            lineTo(w * 0.58f, h * 0.76f)
            lineTo(w * 0.40f, h * 0.60f)
            lineTo(w * 0.18f, h * 0.60f)
            close()
        }
        drawPath(body, color, style = Stroke(stroke, join = StrokeJoin.Round))
        if (muted) {
            // a clean X where the waves would be
            drawLine(color, Offset(w * 0.68f, h * 0.36f), Offset(w * 0.86f, h * 0.64f), stroke)
            drawLine(color, Offset(w * 0.86f, h * 0.36f), Offset(w * 0.68f, h * 0.64f), stroke)
        } else {
            // two open sound waves
            drawArc(
                color,
                startAngle = -52f,
                sweepAngle = 104f,
                useCenter = false,
                topLeft = Offset(w * 0.56f, h * 0.30f),
                size = Size(w * 0.20f, h * 0.40f),
                style = Stroke(stroke),
            )
            drawArc(
                color,
                startAngle = -52f,
                sweepAngle = 104f,
                useCenter = false,
                topLeft = Offset(w * 0.64f, h * 0.18f),
                size = Size(w * 0.34f, h * 0.64f),
                style = Stroke(stroke),
            )
        }
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
