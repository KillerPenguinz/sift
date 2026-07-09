package com.ironclinicgym.sift.ui.board

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ironclinicgym.sift.core.domain.PolicyDecision
import com.ironclinicgym.sift.ui.theme.SiftTheme
import com.ironclinicgym.sift.ui.theme.toColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RedirectPromptSheet(
    prompt: PolicyDecision.RedirectPrompt,
    onChangeDate: () -> Unit,
    onSnooze: () -> Unit,
    onPin: () -> Unit,
    onRemoveDate: () -> Unit,
    onDismiss: () -> Unit,
) {
    val tokens = SiftTheme.tokens
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = tokens.neutrals.surfaceRaised.toColor(),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "This task has a date",
                style = MaterialTheme.typography.headlineSmall,
                color = tokens.neutrals.textPrimary.toColor(),
            )
            Text(
                "Its priority is set by the due date. To move it, try one of these:",
                color = tokens.neutrals.textSecondary.toColor(),
                style = MaterialTheme.typography.bodyMedium,
            )

            RedirectOption(icon = "event", label = "Change the date", onClick = onChangeDate)
            RedirectOption(icon = "schedule", label = "Snooze it", onClick = onSnooze)
            RedirectOption(icon = "push_pin", label = "Pin it to the top", onClick = onPin)
            RedirectOption(icon = "event_busy", label = "Remove the date", onClick = onRemoveDate)

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun RedirectOption(icon: String, label: String, onClick: () -> Unit) {
    val tokens = SiftTheme.tokens
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(tokens.neutrals.surface.toColor())
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MaterialSymbol(icon, tokens.neutrals.textSecondary.toColor(), size = 20.sp)
        Text(label, color = tokens.neutrals.textPrimary.toColor(), style = MaterialTheme.typography.bodyLarge)
    }
}
