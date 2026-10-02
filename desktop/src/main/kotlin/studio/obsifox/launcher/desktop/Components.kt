package studio.obsifox.launcher.desktop

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties

/** Frosted glass card that floats above the wallpaper. */
@Composable
fun ObsiCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    color: Color? = null,
    border: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val o = obsi()
    val shape = RoundedCornerShape(18.dp)
    val fill = color ?: o.glass
    val edge = border ?: o.line
    if (onClick != null) {
        Surface(onClick = onClick, modifier = modifier, shape = shape, color = fill, border = BorderStroke(1.dp, edge)) {
            Column(content = content)
        }
    } else {
        Surface(modifier = modifier, shape = shape, color = fill, border = BorderStroke(1.dp, edge)) {
            Column(content = content)
        }
    }
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true) {
    val o = obsi()
    Button(
        onClick = onClick, modifier = modifier, enabled = enabled, shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = o.accent, contentColor = o.onAccent,
            disabledContainerColor = o.accent.copy(alpha = 0.35f), disabledContentColor = o.onAccent.copy(alpha = 0.6f),
        ),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 11.dp),
    ) {
        if (icon != null) { Icon(icon, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SoftButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, danger: Boolean = false, enabled: Boolean = true) {
    val o = obsi()
    FilledTonalButton(
        onClick = onClick, modifier = modifier, enabled = enabled, shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = o.glassHigh, contentColor = if (danger) o.bad else o.text,
            disabledContainerColor = o.glassLow, disabledContentColor = o.textDim,
        ),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 15.dp, vertical = 8.dp),
    ) {
        if (icon != null) { Icon(icon, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)) }
        Text(text)
    }
}

@Composable
fun IconAction(icon: ImageVector, description: String, onClick: () -> Unit, tint: Color? = null, modifier: Modifier = Modifier) {
    val o = obsi()
    IconButton(onClick = onClick, modifier = modifier.size(36.dp)) { Icon(icon, description, tint = tint ?: o.textDim, modifier = Modifier.size(20.dp)) }
}

@Composable
fun Pill(text: String, color: Color? = null, modifier: Modifier = Modifier) {
    val o = obsi()
    val c = color ?: o.accent
    Box(modifier.clip(RoundedCornerShape(50)).background(c.copy(alpha = 0.18f)).padding(horizontal = 10.dp, vertical = 3.dp)) {
        Text(text, color = c, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

/** Colored circle with the first letter - used for accounts and as the fallback project icon. */
@Composable
fun LetterAvatar(name: String, size: Dp = 44.dp, shape: androidx.compose.ui.graphics.Shape = CircleShape, modifier: Modifier = Modifier) {
    val seed = name.fold(7) { a, c -> a * 31 + c.code }
    val hue = (kotlin.math.abs(seed) % 360).toFloat()
    val a = Color.hsv(hue, 0.55f, 0.85f)
    val b = Color.hsv((hue + 40f) % 360f, 0.65f, 0.55f)
    Box(modifier.size(size).clip(shape).background(Brush.linearGradient(listOf(a, b))), contentAlignment = Alignment.Center) {
        Text(name.trim().take(1).uppercase().ifEmpty { "?" }, color = Color.White, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.42f).sp)
    }
}

/** Remote icon with a letter fallback. */
@Composable
fun RemoteImage(url: String?, name: String, size: Dp, modifier: Modifier = Modifier, shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(12.dp)) {
    val app = LocalApp.current
    var bmp by remember(url) { mutableStateOf(url?.let { app.cachedImage(it) }) }
    LaunchedEffect(url) { if (bmp == null && url != null) bmp = app.image(url) }
    val b = bmp
    if (b != null) {
        Image(b, name, modifier.size(size).clip(shape), contentScale = ContentScale.Crop)
    } else {
        LetterAvatar(name, size, shape, modifier)
    }
}

@Composable
fun FoxMark(size: Dp, tint: Color? = null, modifier: Modifier = Modifier) {
    val o = obsi()
    Image(androidx.compose.ui.graphics.vector.rememberVectorPainter(ObsiIcons.Fox), null, modifier.size(size), colorFilter = ColorFilter.tint(tint ?: o.accent))
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    val o = obsi()
    Row(modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = o.text, modifier = Modifier.weight(1f))
        trailing()
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, text: String? = null, modifier: Modifier = Modifier, action: @Composable () -> Unit = {}) {
    val o = obsi()
    Column(modifier.fillMaxWidth().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(72.dp).clip(CircleShape).background(o.glassHigh), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = o.textDim, modifier = Modifier.size(34.dp))
        }
        Text(title, style = MaterialTheme.typography.titleMedium, color = o.text)
        if (text != null) Text(text, color = o.textDim, fontSize = 14.sp)
        action()
    }
}

/** Glass input field used across settings and dialogs. */
@Composable
fun ObsiField(
    value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier,
    label: String? = null, placeholder: String? = null, isError: Boolean = false,
    singleLine: Boolean = true, minLines: Int = 1, enabled: Boolean = true, trailing: @Composable (() -> Unit)? = null,
) {
    val o = obsi()
    OutlinedTextField(
        value = value, onValueChange = onChange, modifier = modifier.fillMaxWidth(), enabled = enabled,
        label = if (label != null) ({ Text(label) }) else null,
        placeholder = if (placeholder != null) ({ Text(placeholder, color = o.textFaint) }) else null,
        isError = isError,
        singleLine = singleLine, minLines = minLines, shape = RoundedCornerShape(14.dp), trailingIcon = trailing,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = o.glassInput, unfocusedContainerColor = o.glassInput, disabledContainerColor = o.glassLow,
            focusedBorderColor = o.accent, unfocusedBorderColor = o.lineSoft, disabledBorderColor = o.lineSoft, errorBorderColor = o.bad,
            focusedTextColor = o.text, unfocusedTextColor = o.text, cursorColor = o.accent,
            focusedLabelColor = o.accent, unfocusedLabelColor = o.textDim, errorLabelColor = o.bad,
        ),
    )
}

/** Backwards-compatible alias (older screens call LabeledField). */
@Composable
fun LabeledField(
    label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier,
    hint: String? = null, singleLine: Boolean = true, minLines: Int = 1, enabled: Boolean = true, trailing: @Composable (() -> Unit)? = null,
) {
    ObsiField(value, onChange, modifier, label = label, placeholder = hint, singleLine = singleLine, minLines = minLines, enabled = enabled, trailing = trailing)
}

@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier, sub: String? = null) {
    val o = obsi()
    Row(modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, color = o.text)
            if (sub != null) Text(sub, color = o.textDim, fontSize = 12.sp)
        }
        ObsiSwitch(checked, onChange)
    }
}

@Composable
fun ObsiSwitch(checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val o = obsi()
    Switch(
        checked, onChange, modifier,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White, checkedTrackColor = o.accent, checkedBorderColor = o.accent,
            uncheckedThumbColor = o.textDim, uncheckedTrackColor = o.glassLow, uncheckedBorderColor = o.line,
        ),
    )
}

/** Read-only field that opens a menu - options are (value, label) pairs. */
@Composable
fun DropdownField(label: String, selectedLabel: String, options: List<Pair<String, String>>, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val o = obsi()
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedTextField(
            value = selectedLabel, onValueChange = {}, readOnly = true, label = { Text(label) }, singleLine = true,
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = o.glassInput, unfocusedContainerColor = o.glassInput, disabledContainerColor = o.glassLow,
                focusedBorderColor = o.accent, unfocusedBorderColor = o.lineSoft, disabledBorderColor = o.lineSoft,
                focusedTextColor = o.text, unfocusedTextColor = o.text, cursorColor = o.accent,
                focusedLabelColor = o.accent, unfocusedLabelColor = o.textDim,
            ),
            trailingIcon = { Icon(ObsiIcons.ArrowDown, null, tint = o.textDim) },
        )
        // transparent overlay catches the click (a read-only text field swallows it otherwise)
        Box(Modifier.matchParentSize().clickable { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = o.glassHigh) {
            options.forEach { (value, text) ->
                DropdownMenuItem(text = { Text(text, color = o.text) }, onClick = { open = false; onSelect(value) })
            }
        }
    }
}

@Composable
fun ConfirmDialog(title: String, text: String, confirmLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit, danger: Boolean = false) {
    val o = obsi()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text, color = o.textDim) },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirmLabel, color = if (danger) o.bad else o.accent, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("cancel")) } },
        containerColor = o.glassHigh,
        shape = RoundedCornerShape(20.dp),
    )
}

@Composable
fun WideDialog(
    title: String, onDismiss: () -> Unit, width: Dp = 560.dp,
    confirm: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val o = obsi()
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.width(width),
        title = { Text(title) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp), content = content) },
        confirmButton = { confirm?.invoke() },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("close")) } },
        containerColor = o.glassHigh,
        shape = RoundedCornerShape(20.dp),
    )
}

fun formatCount(n: Long): String = when {
    n >= 1_000_000 -> String.format(java.util.Locale.ROOT, "%.1fM", n / 1_000_000.0)
    n >= 1_000 -> String.format(java.util.Locale.ROOT, "%.1fK", n / 1_000.0)
    else -> n.toString()
}

fun formatSize(bytes: Long): String = when {
    bytes >= 1L shl 20 -> String.format(java.util.Locale.ROOT, "%.1f MB", bytes / 1048576.0)
    bytes >= 1L shl 10 -> String.format(java.util.Locale.ROOT, "%.0f KB", bytes / 1024.0)
    else -> "$bytes B"
}

@Composable
fun Dim(text: String, modifier: Modifier = Modifier, size: Int = 13, maxLines: Int = 1) {
    val o = obsi()
    Text(text, color = o.textDim, fontSize = size.sp, lineHeight = (size * 1.5f).sp, maxLines = maxLines, overflow = TextOverflow.Ellipsis, modifier = modifier)
}

@Composable
fun VSpace(h: Int) = Spacer(Modifier.height(h.dp))

@Composable
fun HSpace(w: Int) = Spacer(Modifier.width(w.dp))
