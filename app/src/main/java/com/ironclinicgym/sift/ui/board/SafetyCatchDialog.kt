package com.ironclinicgym.sift.ui.board

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ironclinicgym.sift.core.board.formatOrdinalDate
import com.ironclinicgym.sift.core.domain.SiftTask
import com.ironclinicgym.sift.ui.theme.SiftTheme
import com.ironclinicgym.sift.ui.theme.toColor
import java.time.LocalDate

@Composable
fun SafetyCatchDialog(
    task: SiftTask,
    todayIso: String = remember { LocalDate.now().toString() },
    onFindBetterSpot: () -> Unit,
    onDismiss: () -> Unit,
) {
    val tokens = SiftTheme.tokens
    val due = task.due?.take(10)
    val titleText = when {
        due == null -> "Heads up on this one."
        due == todayIso -> "This one is due today."
        else -> "This one was due ${formatOrdinalDate(due)}."
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = tokens.neutrals.surfaceRaised.toColor(),
        title = {
            Text(
                titleText,
                color = tokens.neutrals.textPrimary.toColor(),
            )
        },
        text = {
            Text(
                "Want a hand with it, or should we find it a better spot?",
                color = tokens.neutrals.textSecondary.toColor(),
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            TextButton(onClick = { onFindBetterSpot(); onDismiss() }) {
                Text("Find it a better spot")
            }
        },
        dismissButton = {
            Box {
                TextButton(enabled = false, onClick = {}) {
                    Text("Want a hand with it?")
                }
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 4.dp, y = (-4).dp),
                    shape = MaterialTheme.shapes.extraSmall,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Text(
                        "Coming soon",
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        },
    )
}
