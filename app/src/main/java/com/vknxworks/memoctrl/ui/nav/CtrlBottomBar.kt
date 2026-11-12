package com.vknxworks.memoctrl.ui.nav

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.vknxworks.memoctrl.ui.theme.CtrlColors

data class TabItem(@StringRes val label: Int, val icon: ImageVector, val selectedIcon: ImageVector)

private val BarShape = RoundedCornerShape(22.dp)
private val ItemShape = RoundedCornerShape(16.dp)

/**
 * The Ctrl floating tab bar: 64dp tall, graphite at 92% with a hairline border. The selected tab is an
 * accent block with its icon and name; the others show just their icon.
 */
@Composable
fun CtrlBottomBar(
    items: List<TabItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    Row(
        modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(BarShape)
            .background(CtrlColors.Surface.copy(alpha = 0.92f))
            .border(1.dp, CtrlColors.ControlOutline, BarShape)
            .padding(horizontal = 8.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEachIndexed { i, item ->
            val selected = i == selectedIndex
            val label = stringResource(item.label)
            val bg by animateColorAsState(if (selected) CtrlColors.Accent else CtrlColors.Surface.copy(alpha = 0f), label = "tab-bg")
            val fg by animateColorAsState(if (selected) CtrlColors.OnAccent else CtrlColors.Muted, label = "tab-fg")
            Row(
                Modifier
                    .heightIn(min = 48.dp)
                    .widthIn(min = 48.dp)
                    .clip(ItemShape)
                    .background(bg)
                    .selectable(
                        selected = selected,
                        role = Role.Tab,
                        onClick = {
                            if (!selected) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelect(i)
                        },
                    )
                    .semantics { contentDescription = label }
                    .padding(horizontal = if (selected) 18.dp else 14.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(if (selected) item.selectedIcon else item.icon, contentDescription = null, tint = fg, modifier = Modifier.size(20.dp))
                AnimatedVisibility(visible = selected, enter = fadeIn() + expandHorizontally(), exit = fadeOut() + shrinkHorizontally()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Spacer(Modifier.width(8.dp))
                        Text(label, style = MaterialTheme.typography.labelLarge, color = fg, maxLines = 1)
                    }
                }
            }
        }
    }
}
