package studio.obsifox.obsilauncher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import studio.obsifox.obsilauncher.ui.theme.LocalObsi

/** Frosted glass card used across every page. */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val obsi = LocalObsi.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        obsi.glass.copy(alpha = obsi.glass.alpha),
                        obsi.glass.copy(alpha = obsi.glass.alpha * 0.82f),
                    )
                )
            )
            .border(1.dp, obsi.glassBorder, RoundedCornerShape(18.dp))
            .padding(horizontal = 18.dp, vertical = 16.dp),
        content = content,
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = LocalObsi.current.textDim,
        modifier = modifier.padding(top = 20.dp, bottom = 10.dp),
    )
}

@Composable
fun ObsiButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    danger: Boolean = false,
) {
    val obsi = LocalObsi.current
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (danger) obsi.danger else obsi.accent,
            contentColor = Color(0xFF17110B),
            disabledContainerColor = obsi.accentDim.copy(alpha = 0.25f),
            disabledContentColor = obsi.textDim,
        ),
        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 12.dp),
        modifier = modifier,
    ) {
        Text(text, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun ObsiGhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val obsi = LocalObsi.current
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = obsi.text,
            disabledContentColor = obsi.textDim,
        ),
        modifier = modifier,
    ) {
        Text(text, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun ObsiTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    TextButton(onClick = onClick, enabled = enabled, modifier = modifier) {
        Text(text, color = LocalObsi.current.accent, fontWeight = FontWeight.SemiBold)
    }
}

/** progress line: label + linear bar, used by installers and the runtime manager. */
@Composable
fun ProgressRow(label: String, fraction: Float?, modifier: Modifier = Modifier) {
    val obsi = LocalObsi.current
    Column(modifier = modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = obsi.textDim)
        if (fraction == null) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .clip(RoundedCornerShape(6.dp)),
                color = obsi.accent,
                trackColor = obsi.accentDim.copy(alpha = 0.25f),
            )
        } else {
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .clip(RoundedCornerShape(6.dp)),
                color = obsi.accent,
                trackColor = obsi.accentDim.copy(alpha = 0.25f),
            )
        }
    }
}

@Composable
fun KeyValueRow(key: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(key, style = MaterialTheme.typography.bodyMedium, color = LocalObsi.current.textDim)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = LocalObsi.current.text,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
