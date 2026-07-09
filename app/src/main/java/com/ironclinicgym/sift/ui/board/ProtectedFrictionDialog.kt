package com.ironclinicgym.sift.ui.board

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.ironclinicgym.sift.core.domain.PolicyDecision
import com.ironclinicgym.sift.ui.theme.SiftTheme
import com.ironclinicgym.sift.ui.theme.toColor

@Composable
fun ProtectedFrictionDialog(
    friction: PolicyDecision.ProtectedFriction,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val tokens = SiftTheme.tokens
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = tokens.neutrals.surfaceRaised.toColor(),
        title = { Text("This task is protected", color = tokens.neutrals.textPrimary.toColor()) },
        text = {
            Text(
                "You marked this as important. Are you sure you want to ${friction.action}?",
                color = tokens.neutrals.textSecondary.toColor(),
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Yes, continue") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Keep it") }
        },
    )
}
