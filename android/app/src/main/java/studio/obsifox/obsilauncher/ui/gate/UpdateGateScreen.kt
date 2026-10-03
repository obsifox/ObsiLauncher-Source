package studio.obsifox.obsilauncher.ui.gate

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import kotlinx.coroutines.launch
import studio.obsifox.obsilauncher.R
import studio.obsifox.obsilauncher.app
import studio.obsifox.obsilauncher.core.update.UpdateGate
import studio.obsifox.obsilauncher.ui.screens.stringResourceCompat

/**
 * The boot gate — mirrors Mojang's own launcher boot screen:
 * a completely black screen, the red MOJANG STUDIOS wordmark up top,
 * a status line + progress, the ObsiLauncher brand bottom-left and a
 * Skip pill bottom-right.
 *
 * Everything is automatic — no questions asked:
 *  · a newer release is found  -> the APK downloads right away
 *  · runtimes / JVM missing    -> they are installed right away
 *  · Skip is disabled while any download runs; once everything is ready
 *    the launcher enters the main UI on its own.
 */
@Composable
fun UpdateGateScreen(onEnter: () -> Unit) {
    val context = LocalContext.current
    val app = context.app
    val gate = app.updateGate
    val step by gate.step.collectAsState()
    val apk by gate.updateApk.collectAsState()

    // run automatically on every open
    LaunchedEffect(Unit) {
        if (step == UpdateGate.Step.Idle) gate.run()
    }

    // automation: update found -> download it immediately, then continue
    // with the runtime check; ready -> walk into the launcher.
    LaunchedEffect(step) {
        when (step) {
            is UpdateGate.Step.UpdateAvailable -> {
                launch { gate.downloadUpdate() }
                launch { gate.checkRuntime() }
            }
            is UpdateGate.Step.UpdateReady -> {
                apk?.let { installApk(context, it) }
                kotlinx.coroutines.delay(1400)
                onEnter()
            }
            UpdateGate.Step.Ready -> {
                kotlinx.coroutines.delay(700)
                onEnter()
            }
            else -> {}
        }
    }

    // Skip is only blocked while something is actually downloading/installing
    val skipEnabled = when (step) {
        is UpdateGate.Step.DownloadingUpdate, is UpdateGate.Step.InstallingRuntime -> false
        else -> true
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {

        // centered status -----------------------------------------------------
        Column(
            Modifier.align(Alignment.Center).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            MojangWordmark()
            Spacer(Modifier.height(34.dp))

            val (dot, title, sub) = when (val s = step) {
                UpdateGate.Step.Idle, UpdateGate.Step.CheckingUpdate ->
                    Triple(Color(0xFFB9AFA6), stringResourceCompat(R.string.gate_checking), stringResourceCompat(R.string.gate_checking_sub))
                is UpdateGate.Step.UpdateAvailable ->
                    Triple(Color(0xFFE5A24C), stringResourceCompat(R.string.gate_update_available, s.tag), stringResourceCompat(R.string.gate_update_sub))
                is UpdateGate.Step.DownloadingUpdate -> {
                    val pct = if (s.total > 0) "${(s.done * 100 / s.total)}%" else "…"
                    Triple(Color(0xFFE5A24C), stringResourceCompat(R.string.gate_downloading, pct), stringResourceCompat(R.string.gate_downloading_sub))
                }
                UpdateGate.Step.UpdateReady ->
                    Triple(Color(0xFF7ED957), stringResourceCompat(R.string.gate_update_ready), stringResourceCompat(R.string.gate_update_ready_sub))
                UpdateGate.Step.UpdateFailed ->
                    Triple(Color(0xFFE5604C), stringResourceCompat(R.string.gate_update_failed), stringResourceCompat(R.string.gate_update_failed_sub))
                UpdateGate.Step.CheckingRuntime ->
                    Triple(Color(0xFFB9AFA6), stringResourceCompat(R.string.gate_runtime_checking), stringResourceCompat(R.string.gate_runtime_checking_sub))
                is UpdateGate.Step.InstallingRuntime ->
                    Triple(Color(0xFFE5A24C), stringResourceCompat(R.string.gate_runtime_installing), stringResourceCompat(R.string.gate_runtime_installing_sub))
                UpdateGate.Step.RuntimeMissing ->
                    Triple(Color(0xFFE5A24C), stringResourceCompat(R.string.gate_runtime_missing), stringResourceCompat(R.string.gate_runtime_missing_sub))
                UpdateGate.Step.RuntimeFailed ->
                    Triple(Color(0xFFE5604C), stringResourceCompat(R.string.gate_runtime_failed), stringResourceCompat(R.string.gate_runtime_missing_sub))
                UpdateGate.Step.Ready ->
                    Triple(Color(0xFF7ED957), stringResourceCompat(R.string.gate_up_to_date), stringResourceCompat(R.string.gate_up_to_date_sub))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).clip(androidx.compose.foundation.shape.CircleShape).background(dot))
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = Color(0xFFF4EFEA))
            }
            Spacer(Modifier.height(6.dp))
            Text(sub, style = MaterialTheme.typography.bodyMedium, color = Color(0xFFB9AFA6))

            Spacer(Modifier.height(18.dp))
            when (val s = step) {
                is UpdateGate.Step.DownloadingUpdate -> {
                    LinearProgressIndicator(
                        progress = { if (s.total > 0) s.done.toFloat() / s.total else 0f },
                        modifier = Modifier.fillMaxWidth(0.34f).height(3.dp).clip(RoundedCornerShape(2.dp)),
                        color = Color(0xFFDB2F26),
                        trackColor = Color(0x24FFFFFF),
                    )
                }
                is UpdateGate.Step.InstallingRuntime -> {
                    LinearProgressIndicator(
                        progress = { s.fraction ?: 0f },
                        modifier = Modifier.fillMaxWidth(0.34f).height(3.dp).clip(RoundedCornerShape(2.dp)),
                        color = Color(0xFFDB2F26),
                        trackColor = Color(0x24FFFFFF),
                    )
                }
                else -> {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(0.34f).height(3.dp).clip(RoundedCornerShape(2.dp)),
                        color = Color(0xFFDB2F26),
                        trackColor = Color(0x1AFFFFFF),
                    )
                }
            }

            // retry / install actions ----------------------------------------
            Spacer(Modifier.height(16.dp))
            when (step) {
                is UpdateGate.Step.UpdateFailed, UpdateGate.Step.RuntimeFailed, UpdateGate.Step.RuntimeMissing -> {
                    GateAction("⟳", stringResourceCompat(R.string.gate_check_again)) {
                        gate.reset()
                        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                            gate.run(skipUpdate = true)
                        }
                    }
                }
                else -> {}
            }
        }

        // bottom-left brand: the uploaded Minecraft-Launcher mark ----------------
        Row(
            Modifier.align(Alignment.BottomStart).padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(R.drawable.obsi_logo),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, Color(0x40FFFFFF), RoundedCornerShape(8.dp)),
            )
            Spacer(Modifier.width(9.dp))
            Text("ObsiLauncher", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = Color(0xFFF4EFEA))
        }

        // bottom-right Skip ------------------------------------------------------
        Row(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(18.dp)
                .alpha(if (skipEnabled) 1f else 0.35f)
                .clip(RoundedCornerShape(999.dp))
                .background(if (skipEnabled) Color(0x2EFFFFFF) else Color(0x14FFFFFF))
                .border(1.dp, Color(0x3DFFFFFF), RoundedCornerShape(999.dp))
                .clickable(enabled = skipEnabled, onClick = onEnter)
                .padding(horizontal = 24.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResourceCompat(R.string.gate_skip),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = Color(0xFFF4EFEA),
            )
        }
    }
}

/** The real red Mojang Studios wordmark (bundled image, as briefed). */
@Composable
private fun MojangWordmark() {
    Image(
        painter = painterResource(R.drawable.mojang_logo),
        contentDescription = "Mojang Studios",
        contentScale = ContentScale.FillWidth,
        modifier = Modifier.fillMaxWidth(0.42f),
    )
}

@Composable
private fun GateAction(glyph: String, label: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(glyph, style = MaterialTheme.typography.titleSmall, color = Color(0xFFB9AFA6))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = Color(0xFFB9AFA6))
    }
}

private fun installApk(context: android.content.Context, file: java.io.File) {
    runCatching {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
