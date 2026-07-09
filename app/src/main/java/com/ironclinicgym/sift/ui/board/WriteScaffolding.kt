package com.ironclinicgym.sift.ui.board

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ironclinicgym.sift.ui.theme.SiftTheme
import com.ironclinicgym.sift.ui.theme.toColor

/**
 * The plain-language consent prompt shown before Sift adds its recurrence columns to a
 * bring-your-own database. Yes proceeds; No degrades gracefully.
 */
@Composable
fun ConsentDialog(message: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val tokens = SiftTheme.tokens
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add recurrence fields?", color = tokens.neutrals.textPrimary.toColor()) },
        text = { Text(message, color = tokens.neutrals.textSecondary.toColor()) },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Yes, add them") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Not now") } },
        containerColor = tokens.neutrals.surfaceRaised.toColor(),
    )
}

/**
 * The single active undo bar. Sits at the bottom, shows the action label and an Undo action.
 * The highlight flash itself is applied per-row via [flashBackground]; this is just the prompt.
 */
@Composable
fun UndoBar(label: String, onUndo: () -> Unit, modifier: Modifier = Modifier) {
    val tokens = SiftTheme.tokens
    Row(
        modifier
            .fillMaxWidth()
            .padding(16.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(tokens.neutrals.textPrimary.toColor())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = tokens.neutrals.bg.toColor(), style = MaterialTheme.typography.bodyMedium)
        Text(
            "Undo",
            color = tokens.neutrals.bg.toColor(),
            fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable(onClick = onUndo).padding(start = 16.dp),
        )
    }
}

/**
 * Returns a background color that flashes to an accent tint and fades back when [flashing] is
 * true, so an undone/changed row visibly signals what changed and where.
 */
@Composable
fun flashBackground(flashing: Boolean, accent: Color): State<Color> {
    val base = SiftTheme.tokens.neutrals.surface.toColor().copy(alpha = 0f)
    return animateColorAsState(
        targetValue = if (flashing) accent.copy(alpha = 0.28f) else base,
        animationSpec = tween(durationMillis = if (flashing) 120 else 900),
        label = "rowFlash",
    )
}

/** A compact icon action used in the task detail sheet (edit, complete, remove, move). */
@Composable
fun ActionChip(icon: String, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val tokens = SiftTheme.tokens
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(tokens.neutrals.surface.toColor())
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        MaterialSymbol(icon, color = tokens.neutrals.textSecondary.toColor(), size = 20.sp)
        Text(label, color = tokens.neutrals.textSecondary.toColor(), style = MaterialTheme.typography.labelSmall)
    }
}
