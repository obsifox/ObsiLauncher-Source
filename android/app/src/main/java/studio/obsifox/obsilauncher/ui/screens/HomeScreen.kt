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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
    val gameState by app.gameManager.state.collectAsState()
    val installing by app.installer.state.collectAsState()
    val loaderInstalling by app.loaders.state.collectAsState()
    val selectedVersion by app.settings.selectedVersion.collectAsState()
    val videoActive by app.wallpaper.active.collectAsState()
    val videoMuted by app.settings.videoMuted.collectAsState()

    val active = instances.firstOrNull { it.id == activeId }
    val account = accounts.firstOrNull { it.id == activeAccountId }
    // v1.13.0 — runtime readiness is read from the SAME store that boots the
    // JVM (ObsiComponents/Internal-*), not from the disconnected pack store
    val major = active?.versionId?.let {
        studio.obsifox.obsilauncher.core.game.LaunchPipeline.javaMajor(context, it)
    } ?: 21
    var runtimeName by remember(active?.versionId) {
        mutableStateOf(studio.obsifox.obsilauncher.core.runtime.ObsiComponents.installedRuntimeName(context, major))
    }
    // refresh after the gate / pre-flight installs one (components unpack in bg)
    androidx.compose.runtime.LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(2_000)
        if (runtimeName == null) {
            studio.obsifox.obsilauncher.core.runtime.ObsiComponents.ensureBundledRuntime(context)
            runtimeName = studio.obsifox.obsilauncher.core.runtime.ObsiComponents.installedRuntimeName(context, major)
        }
    }
    val runtimeReady = runtimeName != null
    val hasVersion = active != null && app.installer.isInstalled(active.versionId)

    // ---- the big button's three states (v1.12.0, the user's PNG palette) ---
    // blue  = the selected version is not downloaded yet   -> "DOWNLOAD"
    // gray  = a download is running; a GREEN fill grows    -> from the
    //         language's start corner (right in FA, left in EN)
    // green = downloaded and ready                         -> "PLAY"
    val runningInstall = installing as? studio.obsifox.obsilauncher.core.game.InstallState.Running
    val runningLoader = loaderInstalling as? studio.obsifox.obsilauncher.core.game.InstallState.Running
    val runtimeInstalling by app.runtimePacks.installing.collectAsState()
    val runtimeProgress by app.runtimePacks.progress.collectAsState()
    val downloadProgress: Float? = when {
        runningInstall != null -> runningInstall.fraction
            ?: if (runningInstall.total > 0) runningInstall.done.toFloat() / runningInstall.total else 0f
        runningLoader != null -> runningLoader.fraction ?: 0.5f
        // v1.12.0 — the runtime/JVM auto-download shows on the very same button
        runtimeInstalling -> runtimeProgress ?: 0.02f
        else -> null
    }
    // v1.12.0 — the runtime must be COMPLETE (libjvm.so really there), not just
    // "a folder exists", or the game dies the moment it is spawned
    val busy = gameState == GameState.RUNNING || gameState == GameState.PREPARING || downloadProgress != null
    val canDownload = !busy &&
        ((selectedVersion.isNotBlank() && !hasVersion) || !runtimeReady)
    val canPlay = hasVersion && account != null && runtimeReady && !busy

    Box(Modifier.fillMaxSize()) {
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
                // v1.11.0 — the video sound toggle also lives here on a fresh
                // install, where the dashboard row below does not exist yet
                if (videoActive.isVideo) {
                    Spacer(Modifier.height(14.dp))
                    MuteButton(
                        muted = videoMuted,
                        onToggle = { app.settings.videoMutedValue = !videoMuted },
                    )
                }
            }
            return@Box
        }

        // readability scrim over the wallpaper (v1.11.0: a touch deeper so
        // labels and pills stay readable on bright artwork — still sharp art)
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(230.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color(0x8C140F0C), Color(0xCC140F0C)),
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
            // status / warning line — only when needed. v1.11.0: never during
            // a running download (the big button IS the progress indicator,
            // the old chip used to collide with the floating top bar)
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
                !canPlay && downloadProgress == null && runningLoader == null -> {
                    StatusChip(
                        text = when {
                            !hasVersion -> stringResourceCompat(R.string.home_no_version)
                            account == null -> stringResourceCompat(R.string.home_no_account)
                            !runtimeReady -> stringResourceCompat(R.string.home_missing_runtime)
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
                            else -> onPlay
                        },
                    )
                }
                // v1.12.0 — the runtime download runs through the same button,
                // but say WHAT is being fetched so the wait is not a mystery
                runtimeInstalling && runningInstall == null -> {
                    StatusChip(
                        text = stringResourceCompat(R.string.home_runtime_preparing),
                        accent = obsi.accent,
                        actionText = stringResourceCompat(R.string.console_title),
                        onAction = onOpenConsole,
                    )
                }
            }
            if (gameState == GameState.RUNNING || gameState == GameState.PREPARING ||
                (!canPlay && runningLoader == null)
            ) {
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
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.padding(bottom = 6.dp),
                ) {
                    // v1.11.0 — the video sound toggle lives with the other
                    // dashboard controls now: always visible, never overlapping
                    // the floating top bar, on either language side
                    if (videoActive.isVideo) {
                        MuteButton(
                            muted = videoMuted,
                            onToggle = { app.settings.videoMutedValue = !videoMuted },
                        )
                    }
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
                    // v1.12.0 — PLAY now runs the pre-flight: missing version
                    // files and a missing/incomplete runtime are downloaded
                    // first, then the game spawns. A missing runtime is thus
                    // fixable straight from the big button.
                    onClick = {
                        when {
                            canPlay -> onPlay()
                            canDownload && !hasVersion -> onDownload()
                            canDownload -> onPlay() // runtime fetch via pre-flight
                        }
                    },
                )
            }
        }
    }
}

/**
 * The big bottom-right button — PLAY when ready, DOWNLOAD otherwise —
 * built pixel-faithful to the buttons the user supplied for v1.12.0
 * (blue_button / gray_button / green_button.png):
 *
 *   [ light top strip ]  ~10% of the height, the classic plastic highlight
 *   [ flat face       ]  the button's main colour
 *   [ dark bevel      ]  ~12% along the bottom, the 3D edge
 *   [ black border    ]  2dp frame all around, sharp corners
 *
 * State colours, exactly as briefed ("از آبی به خاکستری، همزمان با دانلود
 * از خاکستری به سبز"):
 *   BLUE  = not downloaded yet                    -> tap to download
 *   GRAY  = a download is running; a GREEN fill   -> grows from the
 *           language's start corner (right in FA, left in EN)
 *   GREEN = everything installed                  -> PLAY
 *
 * v1.12.0 fix: the button has a FIXED height (84dp) — the fill is painted
 * with drawBehind and can never stretch the layout again.
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
    // fixed geometry — the button can never grow beyond this, no matter
    // what the progress fill does
    val height = 84.dp
    val corner = 2.dp
    val shape = RoundedCornerShape(corner)

    // palettes sampled straight from the user's PNG assets
    class Skin(val top: Color, val face: Color, val bevel: Color)
    val skin = when {
        progress != null -> Skin(Color(0xFF949494), Color(0xFF575757), Color(0xFF434343)) // gray base
        ready            -> Skin(Color(0xFF00D01F), Color(0xFF008318), Color(0xFF042D0A)) // green
        enabled          -> Skin(Color(0xFF5BA5FF), Color(0xFF2C8BFF), Color(0xFF003B83)) // blue
        else             -> Skin(Color(0xFF6A6A6E), Color(0xFF46464C), Color(0xFF1C1C20)) // disabled
    }
    // the fill carries the green skin of the ready button
    val fillFace = Color(0xFF008318)
    val fillTop = Color(0xFF21CB35)

    Box(
        Modifier
            .widthIn(min = 250.dp)
            .height(height)
            .clip(shape)
            .background(skin.face)
            .border(2.dp, Color.Black, shape)
            .drawBehind {
                val strip = size.height * 0.10f
                val bevelH = size.height * 0.12f
                // 1) the light top strip of the BASE skin
                drawRect(skin.top, size = Size(size.width, strip))
                // 2) the progress fill replaces both strip and face in its
                //    region so the green block reads as a real button half
                if (progress != null) {
                    val fraction = progress.coerceIn(0f, 1f)
                    val w = size.width * fraction
                    val start = when (layoutDirection) {
                        androidx.compose.ui.unit.LayoutDirection.Rtl -> size.width - w
                        else -> 0f
                    }
                    drawRect(fillFace, topLeft = Offset(start, strip), size = Size(w, size.height - strip - bevelH))
                    drawRect(fillTop, topLeft = Offset(start, 0f), size = Size(w, strip))
                }
                // 3) the dark 3D bevel along the bottom edge — always on top
                drawRect(skin.bevel, topLeft = Offset(0f, size.height - bevelH), size = Size(size.width, bevelH))
            }
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        Column(
            Modifier.fillMaxHeight().padding(horizontal = 28.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                letterSpacing = 3.sp,
                color = Color.White,
                maxLines = 1,
            )
            if (progress != null) {
                Text(
                    text = "${(progress.coerceIn(0f, 1f) * 100).toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            } else if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.9f),
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * v1.12.0 — the square Minecraft sound button, pixel-faithful to the two
 * assets the user supplied (sound_button.png / m_sound_music.png):
 * black frame, light top strip, dark bottom edge, a chunky white note.
 * GREEN face = sound on, CHARCOAL face = muted.
 */
@Composable
private fun MuteButton(muted: Boolean, onToggle: () -> Unit) {
    val muteDesc = stringResourceCompat(if (muted) R.string.bg_unmute else R.string.bg_mute)
    val face = if (muted) Color(0xFF333333) else Color(0xFF008318)
    val strip = if (muted) Color(0xFF8C8C8C) else Color(0xFF4EFF69)
    val shape = RoundedCornerShape(2.dp)
    Box(
        Modifier
            .size(40.dp)
            .clip(shape)
            .background(face)
            .border(2.dp, Color.Black, shape)
            .drawBehind {
                // the classic plastic anatomy: light strip up top, black edge below
                val s = size.height * 0.16f
                drawRect(strip, size = Size(size.width, s))
                val b = size.height * 0.12f
                drawRect(Color.Black, topLeft = Offset(0f, size.height - b), size = Size(size.width, b))
            }
            .clickable(onClick = onToggle)
            .semantics { contentDescription = muteDesc },
        contentAlignment = Alignment.Center,
    ) {
        MusicNoteIcon(
            color = Color(0xFFEFEFEF),
            modifier = Modifier.size(20.dp),
        )
    }
}

/** the chunky eighth note from the user's sound buttons — head, stem, flag. */
@Composable
private fun MusicNoteIcon(modifier: Modifier = Modifier, color: Color) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        // head: a filled oval sitting bottom-left
        val headW = w * 0.46f
        val headH = h * 0.32f
        val headCx = w * 0.33f
        val headCy = h * 0.72f
        // stem: a thick bar rising from the head's right edge to the top
        val stemW = w * 0.10f
        val stemX = headCx + headW / 2f - stemW
        drawRect(color, topLeft = Offset(stemX, h * 0.10f), size = Size(stemW, headCy - h * 0.10f))
        // flag: a chunky block hanging right from the stem's top
        drawRect(color, topLeft = Offset(stemX, h * 0.10f), size = Size(w * 0.30f, h * 0.26f))
        drawOval(
            color,
            topLeft = Offset(headCx - headW / 2f, headCy - headH / 2f),
            size = Size(headW, headH),
        )
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
