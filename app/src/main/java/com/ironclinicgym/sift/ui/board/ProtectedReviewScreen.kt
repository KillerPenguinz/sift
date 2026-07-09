package com.ironclinicgym.sift.ui.board

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ironclinicgym.sift.ui.theme.SiftTheme
import com.ironclinicgym.sift.ui.theme.toColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProtectedReviewScreen(
    tasks: List<BoardViewModel.ProtectedTaskEntry>,
    onUnprotect: (pageId: String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = SiftTheme.tokens
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Protected tasks",
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                navigationIcon = {
                    Box(
                        Modifier
                            .size(44.dp)
                            .clickable(onClick = onBack),
                        contentAlignment = Alignment.Center,
                    ) {
                        MaterialSymbol(
                            "arrow_back",
                            tokens.neutrals.textPrimary.toColor(),
                            size = 22.sp,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = tokens.neutrals.surface.toColor(),
                    titleContentColor = tokens.neutrals.textPrimary.toColor(),
                ),
            )
        },
        containerColor = tokens.neutrals.bg.toColor(),
    ) { innerPadding ->
        if (tasks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .navigationBarsPadding(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "No protected tasks",
                    style = MaterialTheme.typography.bodyLarge,
                    color = tokens.neutrals.textTertiary.toColor(),
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                items(tasks, key = { it.item.task.pageId }) { entry ->
                    ProtectedTaskRow(
                        entry = entry,
                        onUnprotect = { onUnprotect(entry.item.task.pageId) },
                    )
                    HorizontalDivider(color = tokens.neutrals.border.toColor())
                }
            }
        }
    }
}

@Composable
private fun ProtectedTaskRow(
    entry: BoardViewModel.ProtectedTaskEntry,
    onUnprotect: () -> Unit,
) {
    val tokens = SiftTheme.tokens
    val priorityColors = priorityColorsOf(entry.priorityColorKey)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                entry.item.task.title,
                style = MaterialTheme.typography.bodyLarge,
                color = tokens.neutrals.textPrimary.toColor(),
            )
            Spacer(Modifier.height(4.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Priority badge
                Surface(
                    shape = MaterialTheme.shapes.extraSmall,
                    color = priorityColors.pillBg.toColor(),
                ) {
                    Text(
                        entry.priorityLabel,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = priorityColors.pillText.toColor(),
                    )
                }
                // Bucket label (only shown when a bucket is assigned)
                if (entry.item.bucket.optionId != null) {
                    Text(
                        entry.item.bucket.displayName,
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.neutrals.textTertiary.toColor(),
                    )
                }
            }
        }
        TextButton(onClick = onUnprotect) {
            Text(
                "Unprotect",
                color = tokens.neutrals.textSecondary.toColor(),
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}
