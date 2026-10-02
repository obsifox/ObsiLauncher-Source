package studio.obsifox.launcher.desktop

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.res.useResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val gateRed = Color(0xFFD8423B)

/**
 * The start screen: a minimal branded check before the launcher opens. The check is REAL (our update channel + a probe of the
 * data directory), the phases are just made visible. Offline is its own state - the launcher never claims to be up to date
 * when the check could not run.
 */
@Composable
fun GateScreen() {
    val app = LocalApp.current
    val state by app.gate.collectAsState()
    val rtl = androidx.compose.ui.platform.LocalLayoutDirection.current == LayoutDirection.Rtl

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        // restrained ambient light, nothing more
        Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(gateRed.copy(alpha = 0.07f), Color.Transparent), radius = 900f)))

        // language switch, top end
        Box(Modifier.align(Alignment.TopEnd).padding(24.dp)) {
            LanguageSwitch()
        }

        // the wordmark, centred
        val logo = remember { runCatching { useResource("mojang-wordmark.png", ::loadImageBitmap) }.getOrNull() }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(Modifier.padding(bottom = 140.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (logo != null) Image(logo, "Mojang Studios", Modifier.width(360.dp))
            }
        }

        // status area above the actions
        Box(Modifier.fillMaxSize().padding(bottom = 96.dp), contentAlignment = Alignment.BottomCenter) {
            GateStatus(state)
        }

        // footer: signature at the start edge, actions at the end edge (they swap in RTL automatically)
        Box(Modifier.align(Alignment.BottomStart).padding(28.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FoxMark(30.dp, tint = Color(0xFFB7BEC0))
                Column {
                    androidx.compose.material3.Text(t("gate_brand"), color = Color(0xFFF2F4F4).copy(alpha = 0.9f), fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    androidx.compose.material3.Text(
                        if (state is GateState.Offline) t("gate_footer_offline") else t("gate_footer"),
                        color = Color(0xFF505457), fontSize = 8.sp, letterSpacing = 1.sp,
                    )
                }
            }
        }
        val done = state is GateState.Ready || state is GateState.Available || state is GateState.Offline || state is GateState.Failed
        if (done) {
            val st = state
            Box(Modifier.align(Alignment.BottomEnd).padding(28.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (st is GateState.Available) {
                        SoftButton(t("gate_open_page"), { SystemOpen.browse(st.page) }, icon = ObsiIcons.OpenInNew)
                    }
                    SoftButton(t("gate_recheck"), { app.runGate() }, icon = ObsiIcons.Refresh)
                    PrimaryButton(
                        if (state is GateState.Offline) t("gate_continue_offline") else t("gate_continue"),
                        { app.continueFromGate() }, icon = if (rtl) ObsiIcons.ArrowBack else ObsiIcons.ArrowRight,
                    )
                }
            }
        }
    }
}

@Composable
private fun GateStatus(state: GateState) {
    val (title, detail) = when (state) {
        GateState.Checking -> t("gate_checking") to t("gate_checking_sub")
        GateState.Verifying -> t("gate_verifying") to t("gate_verifying_sub")
        is GateState.Ready -> t("gate_ready") to t("gate_ready_sub")
        is GateState.Available -> t("gate_available") to tf("gate_available_sub", state.remote.version.ifBlank { state.remote.commit.take(7) })
        GateState.Offline -> t("gate_offline") to t("gate_offline_sub")
        is GateState.Failed -> t("gate_failed") to state.reason
    }
    val target = when (state) { GateState.Checking -> 0.56f; GateState.Verifying -> 0.92f; is GateState.Ready, is GateState.Available -> 1f; else -> 0f }
    val fill by animateFloatAsState(target, tween(if (state is GateState.Ready || state is GateState.Available) 300 else 1600), label = "gate")
    val hideTrack = state is GateState.Offline || state is GateState.Failed
    val orb = when (state) {
        is GateState.Ready, is GateState.Available -> Color(0xFF9DC9AE)
        GateState.Offline -> Color(0xFF8B9093)
        is GateState.Failed -> gateRed
        else -> gateRed
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(5.dp).background(orb, androidx.compose.foundation.shape.CircleShape))
            androidx.compose.material3.Text(title, color = Color(0xFFD0D2D2), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
        }
        androidx.compose.material3.Text(detail, color = Color(0xFF6F7376), fontSize = 10.sp)
        if (!hideTrack) {
            Box(Modifier.width(260.dp).padding(top = 6.dp)) {
                Box(Modifier.fillMaxWidth().height(2.dp).background(Color(0xFF2A2A2C)))
                Box(
                    Modifier.fillMaxWidth(fraction = fill).height(2.dp)
                        .background(Brush.horizontalGradient(listOf(Color(0xFF842522), gateRed, Color(0xFFFF8C79)))),
                )
            }
        }
    }
}
