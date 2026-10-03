package studio.obsifox.obsilauncher.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import studio.obsifox.obsilauncher.R
import studio.obsifox.obsilauncher.app
import studio.obsifox.obsilauncher.core.BackgroundMode
import studio.obsifox.obsilauncher.ui.components.GlassCard
import studio.obsifox.obsilauncher.ui.components.ObsiButton
import studio.obsifox.obsilauncher.ui.components.ObsiGhostButton
import studio.obsifox.obsilauncher.ui.components.ObsiTextButton
import studio.obsifox.obsilauncher.ui.theme.LocalObsi

/**
 * First-launch setup wizard (briefed in 1.3.0: "startup like a wizard setup").
 * Four steps: welcome -> background type -> player profile -> ready.
 * The glass panel floats above the live wallpaper/video layer.
 */
@Composable
fun SetupWizard() {
    var step by remember { mutableIntStateOf(0) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 22.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .widthIn(max = 760.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            StepDots(step = step, count = 4)
            Spacer(Modifier.height(14.dp))
            when (step) {
                0 -> WelcomeStep { step = 1 }
                1 -> BackgroundStep { step = 2 }
                2 -> AccountStep { step = 3 }
                else -> DoneStep()
            }
        }
    }
}

@Composable
private fun StepDots(step: Int, count: Int) {
    val obsi = LocalObsi.current
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(count) { i ->
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (i <= step) obsi.accent else obsi.textDim.copy(alpha = 0.35f)),
            )
        }
    }
}

@Composable
private fun WelcomeStep(onStart: () -> Unit) {
    val obsi = LocalObsi.current
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 10.dp)) {
            Text(
                text = stringResource(R.string.wizard_welcome_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.wizard_welcome_body),
                style = MaterialTheme.typography.bodyLarge,
                color = obsi.textDim,
            )
            Spacer(Modifier.height(16.dp))
            ObsiButton(
                text = stringResource(R.string.wizard_start),
                onClick = onStart,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun BackgroundStep(onNext: () -> Unit) {
    val context = LocalContext.current
    val app = context.app
    val obsi = LocalObsi.current
    val mode by app.settings.backgroundMode.collectAsState()

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.wizard_bg_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.wizard_bg_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = obsi.textDim,
        )
        Spacer(Modifier.height(12.dp))

        // landscape: the two choices sit side by side
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
        ) {
            BackgroundChoice(
                title = stringResource(R.string.wizard_bg_video),
                body = stringResource(R.string.wizard_bg_video_desc),
                icon = "▶",
                selected = mode == BackgroundMode.VIDEO,
                onSelect = { app.settings.backgroundModeValue = BackgroundMode.VIDEO },
                modifier = Modifier.weight(1f),
            )
            BackgroundChoice(
                title = stringResource(R.string.wizard_bg_wallpaper),
                body = stringResource(R.string.wizard_bg_wallpaper_desc),
                icon = "❖",
                selected = mode == BackgroundMode.WALLPAPER,
                onSelect = { app.settings.backgroundModeValue = BackgroundMode.WALLPAPER },
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(14.dp))
        ObsiButton(
            text = stringResource(R.string.wizard_next),
            onClick = onNext,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun BackgroundChoice(
    title: String,
    body: String,
    icon: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val obsi = LocalObsi.current
    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .background(if (selected) obsi.accentDim else obsi.glass)
            .clickable { onSelect() }
            .padding(14.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = icon,
                    style = MaterialTheme.typography.titleLarge,
                    color = if (selected) obsi.accent else obsi.textDim,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (selected) obsi.accent else obsi.text,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = obsi.textDim,
            )
        }
    }
}

@Composable
private fun AccountStep(onNext: () -> Unit) {
    val context = LocalContext.current
    val app = context.app
    val obsi = LocalObsi.current
    val accounts by app.accounts.accounts.collectAsState()
    val activeId by app.accounts.activeId.collectAsState()
    val existing = accounts.firstOrNull { it.id == activeId }
    var nameDraft by remember { mutableStateOf(existing?.name ?: "") }
    var error by remember { mutableStateOf(false) }

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.wizard_account_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.wizard_account_body),
            style = MaterialTheme.typography.bodyMedium,
            color = obsi.textDim,
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = nameDraft,
            onValueChange = {
                nameDraft = it
                error = false
            },
            label = { Text(stringResource(R.string.accounts_name_hint)) },
            singleLine = true,
            isError = error,
            modifier = Modifier.fillMaxWidth(),
        )
        if (existing != null) {
            Text(
                text = stringResource(R.string.wizard_account_existing, existing.name),
                style = MaterialTheme.typography.labelMedium,
                color = obsi.textDim,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        ObsiButton(
            text = stringResource(R.string.wizard_next),
            onClick = {
                val target = nameDraft.trim()
                if (existing != null) {
                    onNext()
                } else {
                    val created = runCatching { app.accounts.add(target) }.isSuccess
                    if (created) onNext() else error = true
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        if (error) {
            Text(
                text = stringResource(R.string.wizard_account_error),
                style = MaterialTheme.typography.bodyMedium,
                color = obsi.danger,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun DoneStep() {
    val context = LocalContext.current
    val app = context.app
    val obsi = LocalObsi.current
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.wizard_done_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.wizard_done_body),
            style = MaterialTheme.typography.bodyLarge,
            color = obsi.textDim,
        )
        Spacer(Modifier.height(16.dp))
        ObsiButton(
            text = stringResource(R.string.wizard_enter),
            onClick = { app.settings.setupDoneValue = true },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
