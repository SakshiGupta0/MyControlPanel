package app.mycontrolpanel.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.mycontrolpanel.core.DAY_SHORT
import app.mycontrolpanel.core.fmtMinutes
import kotlinx.coroutines.launch

val CardShape = RoundedCornerShape(24.dp)
val PillShape = RoundedCornerShape(50)

@Composable
fun P(): Palette = LocalPalette.current

@Composable
fun PanelCard(modifier: Modifier = Modifier, strong: Boolean = false, padding: Dp = 22.dp, content: @Composable () -> Unit) {
    val p = P()
    Box(
        modifier
            .clip(CardShape)
            .background(if (strong) p.cardStrong else p.card)
            .border(1.dp, p.line.copy(alpha = if (p.dark) 0.6f else 0.7f), CardShape)
            .padding(padding)
    ) { content() }
}

@Composable
fun T(text: String, style: TextStyle = Type.body, color: Color? = null, modifier: Modifier = Modifier, align: TextAlign? = null, maxLines: Int = Int.MAX_VALUE) {
    Text(text, modifier = modifier, style = style, color = color ?: P().text, textAlign = align, maxLines = maxLines)
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, tag: String? = null, enabled: Boolean = true, tall: Dp = 56.dp, style: TextStyle = Type.label) {
    val p = P()
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = tall).let { if (tag != null) it.testTag(tag) else it },
        shape = PillShape,
        colors = ButtonDefaults.buttonColors(containerColor = p.accent, contentColor = p.onAccent,
            disabledContainerColor = p.cardStrong, disabledContentColor = p.muted),
        contentPadding = PaddingValues(horizontal = 28.dp, vertical = 12.dp),
    ) { Text(text, style = style) }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, tag: String? = null, enabled: Boolean = true, color: Color? = null) {
    val p = P()
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 52.dp).let { if (tag != null) it.testTag(tag) else it },
        shape = PillShape,
        border = androidx.compose.foundation.BorderStroke(1.5.dp, (color ?: p.line)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = color ?: p.text),
        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 10.dp),
    ) { Text(text, style = Type.label) }
}

@Composable
fun QuietButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, tag: String? = null, color: Color? = null) {
    TextButton(onClick = onClick, modifier = modifier.heightIn(min = 48.dp).let { if (tag != null) it.testTag(tag) else it }) {
        Text(text, style = Type.label, color = color ?: P().accent)
    }
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, tag: String? = null) {
    val p = P()
    Box(
        modifier
            .heightIn(min = 48.dp)
            .clip(PillShape)
            .background(if (selected) p.accent else p.card)
            .border(1.5.dp, if (selected) p.accent else p.line, PillShape)
            .clickable(onClick = onClick)
            .let { if (tag != null) it.testTag(tag) else it }
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = Type.label, color = if (selected) p.onAccent else p.text) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DayChips(days: Set<Int>, onChange: (Set<Int>) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            (1..7).forEach { d ->
                Chip(DAY_SHORT[d - 1], d in days, { onChange(if (d in days) days - d else days + d) }, tag = "day_$d")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            QuietButton("Every day", { onChange((1..7).toSet()) })
            QuietButton("Weekdays", { onChange(setOf(1, 2, 3, 4, 5)) })
            QuietButton("Weekends", { onChange(setOf(6, 7)) })
        }
    }
}

@Composable
fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit, sub: String? = null, tag: String? = null) {
    val p = P()
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(12.dp)).clickable { onChange(!checked) }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            T(label, Type.label)
            if (sub != null) T(sub, Type.small, p.muted)
        }
        Spacer(Modifier.width(16.dp))
        Switch(
            checked = checked, onCheckedChange = onChange,
            modifier = if (tag != null) Modifier.testTag(tag) else Modifier,
            colors = SwitchDefaults.colors(checkedTrackColor = p.accent, checkedThumbColor = p.onAccent,
                uncheckedTrackColor = p.cardStrong, uncheckedBorderColor = p.line, uncheckedThumbColor = p.muted),
        )
    }
}

@Composable
fun Stepper(label: String, value: Int, onChange: (Int) -> Unit, range: IntRange, step: Int = 1, format: (Int) -> String = { it.toString() }, tag: String? = null) {
    val p = P()
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        T(label, Type.label, modifier = Modifier.weight(1f))
        RoundIconButton("−", { onChange((value - step).coerceIn(range)) }, enabled = value - step >= range.first, tag = tag?.let { "${it}_minus" })
        T(format(value), Type.heading, modifier = Modifier.widthIn(min = 110.dp).padding(horizontal = 8.dp), align = TextAlign.Center)
        RoundIconButton("+", { onChange((value + step).coerceIn(range)) }, enabled = value + step <= range.last, tag = tag?.let { "${it}_plus" })
    }
}

@Composable
fun RoundIconButton(glyph: String, onClick: () -> Unit, enabled: Boolean = true, size: Dp = 52.dp, tag: String? = null, filled: Boolean = false) {
    val p = P()
    Box(
        Modifier.size(size).clip(PillShape)
            .background(if (filled) p.accent else p.cardStrong)
            .clickable(enabled = enabled, onClick = onClick)
            .let { if (tag != null) it.testTag(tag) else it },
        contentAlignment = Alignment.Center,
    ) { Text(glyph, style = Type.heading, color = if (!enabled) p.line else if (filled) p.onAccent else p.text) }
}

@Composable
fun PanelTextField(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier, singleLine: Boolean = true, tag: String? = null) {
    val p = P()
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, singleLine = singleLine,
        textStyle = Type.body.copy(color = p.text),
        modifier = modifier.fillMaxWidth().let { if (tag != null) it.testTag(tag) else it },
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = p.accent, unfocusedBorderColor = p.line,
            focusedLabelColor = p.accent, unfocusedLabelColor = p.muted, cursorColor = p.accent),
    )
}

/** Shows a time; tapping opens a picker. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeField(label: String, minutes: Int, use24h: Boolean, onPick: (Int) -> Unit, tag: String? = null) {
    val p = P()
    var open by remember { mutableStateOf(false) }
    Column(Modifier.clip(RoundedCornerShape(16.dp)).border(1.5.dp, p.line, RoundedCornerShape(16.dp))
        .clickable { open = true }.let { if (tag != null) it.testTag(tag) else it }.padding(horizontal = 20.dp, vertical = 10.dp)) {
        T(label, Type.small, p.muted)
        T(fmtMinutes(minutes, use24h), Type.title)
    }
    if (open) {
        val state = rememberTimePickerState(initialHour = minutes / 60, initialMinute = minutes % 60, is24Hour = use24h)
        AlertDialog(
            onDismissRequest = { open = false },
            containerColor = p.card,
            title = { T(label, Type.heading) },
            text = {
                TimePicker(state = state, colors = TimePickerDefaults.colors(
                    clockDialColor = p.cardStrong, selectorColor = p.accent, timeSelectorSelectedContainerColor = p.accentSoft,
                    periodSelectorSelectedContainerColor = p.accentSoft, timeSelectorUnselectedContainerColor = p.cardStrong,
                ))
            },
            confirmButton = { PrimaryButton("Set", { onPick(state.hour * 60 + state.minute); open = false }) },
            dismissButton = { QuietButton("Cancel", { open = false }) },
        )
    }
}

val ICONS = listOf("☀️", "💼", "🌿", "🌇", "🌙", "🏋️", "✈️", "🤒", "🎯", "🎉", "💄", "📚", "🧘", "🍳", "🧺", "💧", "🔑", "😴", "🫧", "✨", "⭐", "🎧", "🛒", "🌱")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IconPicker(selected: String, onPick: (String) -> Unit) {
    val p = P()
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ICONS.forEach { ic ->
            Box(
                Modifier.size(52.dp).clip(RoundedCornerShape(14.dp))
                    .background(if (ic == selected) p.accentSoft else Color.Transparent)
                    .border(if (ic == selected) 2.dp else 0.dp, if (ic == selected) p.accent else Color.Transparent, RoundedCornerShape(14.dp))
                    .clickable { onPick(ic) },
                contentAlignment = Alignment.Center,
            ) { Text(ic, style = Type.heading) }
        }
    }
}

/** Deliberate exit (PRD §17): press and hold ~1.2 s. A quick tap only shows a hint. */
@Composable
fun HoldToExitButton(onExit: () -> Unit, tag: String = "holdExit") {
    val p = P()
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var hint by remember { mutableStateOf(false) }
    Column(horizontalAlignment = Alignment.End) {
        Box(
            Modifier.widthIn(min = 150.dp).heightIn(min = 52.dp).clip(PillShape)
                .border(1.5.dp, p.line, PillShape)
                .testTag(tag)
                .pointerInput(Unit) {
                    detectTapGestures(onPress = {
                        hint = true
                        val job = scope.launch {
                            progress.snapTo(0f)
                            progress.animateTo(1f, tween(1200, easing = LinearEasing))
                            if (progress.value >= 1f) onExit()
                        }
                        tryAwaitRelease()
                        if (progress.value < 1f) { job.cancel(); scope.launch { progress.snapTo(0f) } }
                    })
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            val fill = p.cardStrong
            Box(Modifier.matchParentSize().drawBehind {
                drawRect(fill, size = androidx.compose.ui.geometry.Size(size.width * progress.value, size.height))
            })
            Text("Hold to exit", style = Type.label, color = p.muted, modifier = Modifier.align(Alignment.Center).padding(horizontal = 20.dp))
        }
        if (hint && progress.value == 0f) T("Press and hold", Type.small, p.muted, Modifier.padding(top = 4.dp, end = 8.dp))
    }
}

@Composable
fun ConfirmDialog(
    title: String, body: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit,
    danger: Boolean = false, dismiss: String = "Cancel",
) {
    val p = P()
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = p.card,
        modifier = Modifier.testTag("confirmDialog"),
        title = { T(title, Type.title) },
        text = { T(body, Type.body, p.muted) },
        confirmButton = {
            Button(onClick = onConfirm, shape = PillShape, modifier = Modifier.heightIn(min = 52.dp).testTag("confirmButton"),
                colors = ButtonDefaults.buttonColors(containerColor = if (danger) p.danger else p.accent, contentColor = Color.White)) {
                Text(confirm, style = Type.label)
            }
        },
        dismissButton = { SecondaryButton(dismiss, onDismiss, tag = "cancelButton") },
    )
}

@Composable
fun Dot(color: Color, size: Dp = 10.dp) = Box(Modifier.size(size).clip(PillShape).background(color))
