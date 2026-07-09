package com.ironclinicgym.sift.ui.board

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.ironclinicgym.sift.ui.theme.SiftTheme
import com.ironclinicgym.sift.ui.theme.toColor

@Composable
fun SafetyCatchDialog(
    taskTitle: String,
    bandLabel: String,
    onAcknowledge: () -> Unit,
    onSnooze: () -> Unit,
    onDismiss: () -> Unit,
) {
    val tokens = SiftTheme.tokens
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = tokens.neutrals.surfaceRaised.toColor(),
        title = { Text("Heads up", color = tokens.neutrals.textPrimary.toColor()) },
        text = {
            Text(
                "\"$taskTitle\" just reached $bandLabel. This task has been rescheduled before. Ready to tackle it, or push it back?",
                color = tokens.neutrals.textSecondary.toColor(),
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            TextButton(onClick = onAcknowledge) { Text("Got it, I'll handle it") }
        },
        dismissButton = {
            TextButton(onClick = onSnooze) { Text("Push it back") }
        },
    )
}
