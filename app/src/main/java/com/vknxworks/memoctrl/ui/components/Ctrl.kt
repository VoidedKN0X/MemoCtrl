package com.vknxworks.memoctrl.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.vknxworks.memoctrl.R
import com.vknxworks.memoctrl.ui.nav.LocalNavigator
import com.vknxworks.memoctrl.ui.theme.CtrlColors
import com.vknxworks.memoctrl.ui.theme.CtrlShapes
import com.vknxworks.memoctrl.ui.theme.CtrlType
import com.vknxworks.memoctrl.ui.theme.Gutter

// Shared building blocks of the Ctrl / Signal look.

// ---------- Text ----------

/** A section label: mono, uppercase, wide tracking, muted. */
@Composable
fun CtrlLabel(text: String, modifier: Modifier = Modifier, color: Color = CtrlColors.Muted) {
    Text(text.uppercase(), style = CtrlType.Label, color = color, modifier = modifier, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/** "MEMO/CTRL" with the slash in the accent colour. */
@Composable
fun CtrlWordmark(modifier: Modifier = Modifier, name: String = "MEMO") {
    val text = remember(name) {
        buildAnnotatedString {
            append(name)
            withStyle(SpanStyle(color = CtrlColors.Accent)) { append("/") }
            append("CTRL")
        }
    }
    Text(text, style = CtrlType.Wordmark, color = CtrlColors.Text, modifier = modifier, maxLines = 1)
}

/** The 8dp square status dot. */
@Composable
fun CtrlDot(color: Color, modifier: Modifier = Modifier) {
    Box(modifier.size(8.dp).clip(CtrlShapes.Dot).background(color))
}

/** Trailing mono status, accent when active, grey when not, with its dot. */
@Composable
fun CtrlStatus(text: String, active: Boolean, modifier: Modifier = Modifier) {
    val color = if (active) CtrlColors.Accent else CtrlColors.Inactive
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(text.uppercase(), style = CtrlType.MonoStrong, color = color, maxLines = 1)
        Spacer(Modifier.width(8.dp))
        CtrlDot(color)
    }
}

// ---------- Top bars ----------

/** A 44dp square button in the top-bar style: surface fill, hairline border. */
@Composable
fun CtrlSquareButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = CtrlColors.Text,
) {
    Box(
        modifier
            .size(44.dp)
            .clip(CtrlShapes.Control)
            .background(CtrlColors.Surface)
            .border(1.dp, CtrlColors.ControlOutline, CtrlShapes.Control)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .alpha(if (enabled) 1f else 0.4f),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, Modifier.size(20.dp), tint = tint)
    }
}

/**
 * Top bar of the main tabs: the wordmark on the left, then any [actions], then your profile picture
 * (which opens the You page and settings). No fill: it sits on the background.
 */
@Composable
fun CtrlTopBar(
    modifier: Modifier = Modifier,
    padded: Boolean = true,
    showProfile: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .padding(horizontal = if (padded) Gutter else 0.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CtrlWordmark()
        Spacer(Modifier.weight(1f))
        actions()
        if (showProfile) {
            Spacer(Modifier.width(8.dp))
            ProfileButton()
        }
    }
}

/** Top bar of pushed screens: a square back button and the title. Applies the status bar inset itself. */
@Composable
fun CtrlBackBar(
    title: String,
    showBack: Boolean = true,
    onBack: (() -> Unit)? = null,
    backIcon: ImageVector = Icons.AutoMirrored.Outlined.ArrowBack,
    backLabel: String = stringResource(R.string.back),
    actions: @Composable RowScope.() -> Unit = {},
) {
    val nav = LocalNavigator.current
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .heightIn(min = 64.dp)
            .padding(start = Gutter, end = 12.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showBack) {
            CtrlSquareButton(backIcon, backLabel, onClick = { if (onBack != null) onBack() else nav.back() })
            Spacer(Modifier.width(14.dp))
        }
        Text(
            title,
            style = MaterialTheme.typography.titleLarge.copy(fontSize = MaterialTheme.typography.headlineSmall.fontSize),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        actions()
    }
}

/** Big tab title under the top bar, with an optional mono label above it. */
@Composable
fun CtrlHeading(title: String, modifier: Modifier = Modifier, label: String? = null) {
    Column(modifier.fillMaxWidth().padding(start = Gutter, end = Gutter, top = 4.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (label != null) CtrlLabel(label)
        Text(title, style = MaterialTheme.typography.headlineLarge, color = CtrlColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** The profile picture in the top bar: initials on a rounded square, an accent badge while all is well. */
@Composable
fun CtrlAvatar(
    initials: String,
    onClick: () -> Unit,
    contentDescription: String,
    connected: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(modifier.size(44.dp)) {
        Box(
            Modifier
                .size(44.dp)
                .clip(CtrlShapes.Control)
                .background(CtrlColors.Surface)
                .border(1.dp, CtrlColors.ControlOutline, CtrlShapes.Control)
                .clickable(role = Role.Button, onClickLabel = contentDescription, onClick = onClick)
                .semantics(mergeDescendants = true) { this.contentDescription = contentDescription },
            contentAlignment = Alignment.Center,
        ) {
            Text(initials, style = MaterialTheme.typography.titleSmall, color = CtrlColors.Text)
        }
        if (connected) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .offset(3.dp, 3.dp)
                    .size(10.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(CtrlColors.Background)
                    .padding(2.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(CtrlColors.Accent),
            )
        }
    }
}

/** Up to two initials of a name: "Axel Smith" -> "AS", "axel" -> "AX". */
fun initialsOf(name: String): String {
    val words = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return when {
        words.isEmpty() -> ""
        words.size == 1 -> words[0].take(2)
        else -> "${words[0].first()}${words[1].first()}"
    }.uppercase()
}

// ---------- Cards ----------

/** Secondary card: surface fill, 1dp hairline border, 22dp corners. */
@Composable
fun CtrlCard(
    modifier: Modifier = Modifier,
    borderColor: Color = CtrlColors.Outline,
    shape: Shape = CtrlShapes.Card,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        shape = shape,
        color = CtrlColors.Surface,
        contentColor = CtrlColors.Text,
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(contentPadding), content = content)
    }
}

/** The one most important thing on a screen: accent fill, dark content, a mono label row and a giant number. */
@Composable
fun CtrlHeroCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    labelEnd: String? = null,
    unit: String? = null,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    Surface(
        shape = CtrlShapes.Hero,
        color = CtrlColors.Accent,
        contentColor = CtrlColors.OnAccent,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(label.uppercase(), style = CtrlType.Label.copy(fontWeight = CtrlType.MonoStrong.fontWeight), color = CtrlColors.OnAccent, modifier = Modifier.weight(1f), maxLines = 1)
                if (labelEnd != null) Text(labelEnd.uppercase(), style = CtrlType.MonoStrong, color = CtrlColors.OnAccent, maxLines = 1)
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, style = CtrlType.Readout, color = CtrlColors.OnAccent, maxLines = 1)
                if (unit != null) {
                    Spacer(Modifier.width(8.dp))
                    Text(unit, style = MaterialTheme.typography.headlineSmall, color = CtrlColors.OnAccent, maxLines = 1, modifier = Modifier.padding(bottom = 6.dp))
                }
            }
            content()
        }
    }
}

/** Draws the 1dp row divider along the top edge. */
fun Modifier.topDivider(color: Color = CtrlColors.Divider): Modifier = drawBehind {
    drawRect(color, size = Size(size.width, 1.dp.toPx()))
}

/**
 * Hides the first row's top divider: the content is lifted by one divider's height and the card
 * clips it, so only the dividers between rows show.
 */
fun Modifier.hideFirstDivider(): Modifier = layout { measurable, constraints ->
    val lift = 1.dp.roundToPx()
    val p = measurable.measure(constraints)
    layout(p.width, (p.height - lift).coerceAtLeast(0)) { p.place(0, -lift) }
}

// ---------- Controls ----------

/** The Ctrl switch: a 52x32 rounded rectangle with a 24dp knob. */
@Composable
fun CtrlSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val knobX by animateDpAsState(if (checked) 20.dp else 0.dp, label = "knob")
    val track by animateColorAsState(if (checked) CtrlColors.Accent else CtrlColors.SwitchOffTrack, label = "track")
    val knob by animateColorAsState(if (checked) CtrlColors.OnAccent else CtrlColors.Inactive, label = "knob-color")
    val toggle = if (onCheckedChange != null) {
        Modifier.toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
    } else Modifier
    Box(
        modifier
            .size(width = 52.dp, height = 32.dp)
            .alpha(if (enabled) 1f else 0.38f)
            .clip(RoundedCornerShape(10.dp))
            .background(track)
            .then(toggle)
            .padding(4.dp),
    ) {
        Box(Modifier.offset(x = knobX).size(24.dp).clip(RoundedCornerShape(7.dp)).background(knob))
    }
}

/** Primary button: accent fill, dark text, 56dp tall. */
@Composable
fun CtrlButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    container: Color = CtrlColors.Accent,
    content: Color = CtrlColors.OnAccent,
    height: Dp = 56.dp,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = height),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = content,
            disabledContainerColor = CtrlColors.Raised,
            disabledContentColor = CtrlColors.Inactive,
        ),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(text, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Secondary button: surface fill with a hairline border. */
@Composable
fun CtrlSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    content: Color = CtrlColors.Text,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 48.dp),
        shape = CtrlShapes.Button,
        border = BorderStroke(1.dp, CtrlColors.ControlOutline),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = CtrlColors.Surface, contentColor = content),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Colours for Material text fields in the Ctrl look (used by OutlinedTextField in dialogs). */
@Composable
fun ctrlOutlinedFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = CtrlColors.Surface,
    unfocusedContainerColor = CtrlColors.Surface,
    disabledContainerColor = CtrlColors.Surface,
    focusedBorderColor = CtrlColors.Accent,
    unfocusedBorderColor = CtrlColors.ControlOutline,
    disabledBorderColor = CtrlColors.Outline,
    cursorColor = CtrlColors.Accent,
    focusedLabelColor = CtrlColors.Accent,
    unfocusedLabelColor = CtrlColors.Muted,
)

/** Text field: surface fill, 1dp border, 16dp corners; the border and cursor turn accent while focused. */
@Composable
fun CtrlTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    singleLine: Boolean = true,
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = CtrlShapes.Button
    TextField(
        value = value,
        onValueChange = onValueChange,
        label = if (label != null) { { Text(label) } } else null,
        placeholder = if (placeholder != null) { { Text(placeholder) } } else null,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        singleLine = singleLine,
        enabled = enabled,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        visualTransformation = visualTransformation,
        textStyle = textStyle,
        interactionSource = interaction,
        shape = shape,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = CtrlColors.Surface,
            unfocusedContainerColor = CtrlColors.Surface,
            disabledContainerColor = CtrlColors.Surface,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            cursorColor = CtrlColors.Accent,
            focusedLabelColor = CtrlColors.Accent,
            unfocusedLabelColor = CtrlColors.Muted,
            focusedLeadingIconColor = CtrlColors.Muted,
            unfocusedLeadingIconColor = CtrlColors.Muted,
            focusedTrailingIconColor = CtrlColors.Muted,
            unfocusedTrailingIconColor = CtrlColors.Muted,
        ),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, if (focused) CtrlColors.Accent else CtrlColors.ControlOutline, shape),
    )
}

/** Segmented buttons: accent for the chosen one, surface for the rest. Pair with [ctrlSegmentShape]. */
@Composable
fun ctrlSegmentColors() = SegmentedButtonDefaults.colors(
    activeContainerColor = CtrlColors.Accent,
    activeContentColor = CtrlColors.OnAccent,
    activeBorderColor = CtrlColors.Accent,
    inactiveContainerColor = CtrlColors.Surface,
    inactiveContentColor = CtrlColors.Text,
    inactiveBorderColor = CtrlColors.ControlOutline,
)

@Composable
fun ctrlSegmentShape(index: Int, count: Int) = SegmentedButtonDefaults.itemShape(index, count, RoundedCornerShape(14.dp))

// ---------- Dialogs ----------

/** AlertDialog on the Ctrl surface: 26dp corners and a hairline border. */
@Composable
fun CtrlAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    properties: DialogProperties = DialogProperties(),
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        modifier = modifier.border(1.dp, CtrlColors.Outline, CtrlShapes.Sheet),
        dismissButton = dismissButton,
        icon = icon,
        title = title,
        text = text,
        shape = CtrlShapes.Sheet,
        containerColor = CtrlColors.Surface,
        iconContentColor = CtrlColors.Accent,
        titleContentColor = CtrlColors.Text,
        textContentColor = CtrlColors.Muted,
        tonalElevation = 0.dp,
        properties = properties,
    )
}

/** Shape of bottom sheets: 26dp top corners. */
val CtrlSheetShape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)

/** Hairline border along a bottom sheet's rounded top. */
fun Modifier.sheetBorder(): Modifier = border(1.dp, CtrlColors.Outline, CtrlSheetShape)

// ---------- Progress ----------

/** A 6dp progress bar: [fraction] of the track filled. */
@Composable
fun CtrlProgressBar(fraction: Float, modifier: Modifier = Modifier, color: Color = CtrlColors.Text) {
    Box(modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(CtrlColors.ProgressTrack)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(6.dp).clip(RoundedCornerShape(3.dp)).background(color))
    }
}
