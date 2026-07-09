package com.ironclinicgym.sift.ui.board

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ironclinicgym.sift.core.board.PriorityView
import com.ironclinicgym.sift.ui.theme.SiftTheme
import com.ironclinicgym.sift.ui.theme.toColor

/**
 * Bottom sheet for picking a new priority for an undated task.
 * Shows all priorities except the task's current one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PriorityPickerSheet(
    priorities: List<PriorityView>,
    currentPriorityOptionId: String?,
    onSelect: (PriorityView) -> Unit,
    onDismiss: () -> Unit,
) {
    val tokens = SiftTheme.tokens
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = tokens.neutrals.surfaceRaised.toColor(),
    ) {
        Text(
            "Move to",
            style = MaterialTheme.typography.titleMedium,
            color = tokens.neutrals.textPrimary.toColor(),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        priorities
            .filter { it.optionId != currentPriorityOptionId }
            .forEach { priority ->
                ListItem(
                    headlineContent = {
                        Text(
                            priority.displayName,
                            color = tokens.neutrals.textPrimary.toColor(),
                        )
                    },
                    colors = ListItemDefaults.colors(
                        containerColor = tokens.neutrals.surfaceRaised.toColor(),
                    ),
                    modifier = Modifier.clickable {
                        onSelect(priority)
                        onDismiss()
                    },
                )
            }
        Spacer(Modifier.height(16.dp))
        Spacer(Modifier.navigationBarsPadding())
    }
}
