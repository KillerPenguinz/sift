package com.ironclinicgym.sift.ui.board

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ironclinicgym.sift.core.domain.SiftNotification
import com.ironclinicgym.sift.ui.common.SiftWordmark
import com.ironclinicgym.sift.ui.theme.Bricolage
import com.ironclinicgym.sift.ui.theme.SiftTheme
import com.ironclinicgym.sift.ui.theme.toColor

/**
 * Notification center: the chronological (newest first) log of actionable notifications,
 * opened from the menu hub. The unread badge is cleared in bulk the moment the menu hub opens
 * (see [BoardViewModel.markNotificationsRead]), but entries themselves stay listed here after
 * being read; nothing disappears just because it was seen.
 */
@Composable
fun NotificationCenterScreen(
    viewModel: BoardViewModel,
    onBack: () -> Unit,
    onAction: (route: String) -> Unit,
) {
    val tokens = SiftTheme.tokens
    val topInset = WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding()
    val notifications by viewModel.notifications.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .background(tokens.neutrals.bg.toColor())
            .padding(top = topInset)
            .navigationBarsPadding(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(40.dp).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                MaterialSymbol("arrow_back", tokens.neutrals.textSecondary.toColor(), size = 22.sp, filled = false)
            }
            Spacer(Modifier.weight(1f))
            SiftWordmark()
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.size(40.dp))
        }

        Text(
            "Notifications",
            fontFamily = Bricolage,
            fontWeight = FontWeight.Bold,
            fontSize = 28.sp,
            color = tokens.neutrals.textPrimary.toColor(),
            modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 24.dp),
        )

        if (notifications.isEmpty()) {
            Text(
                "No notifications yet.",
                color = tokens.neutrals.textTertiary.toColor(),
                fontSize = 15.sp,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp),
            )
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(notifications, key = { it.id }) { notification ->
                    NotificationRow(notification, onAction)
                }
            }
        }
    }
}

@Composable
private fun NotificationRow(
    notification: SiftNotification,
    onAction: (route: String) -> Unit,
) {
    val tokens = SiftTheme.tokens
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
            MaterialSymbol(notification.icon, tokens.neutrals.textSecondary.toColor(), size = 22.sp, filled = true)
            if (!notification.isRead) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(tokens.overdueText.toColor()),
                )
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                notification.message,
                color = tokens.neutrals.textPrimary.toColor(),
                fontSize = 15.sp,
                fontWeight = if (notification.isRead) FontWeight.Normal else FontWeight.SemiBold,
            )
            Text(
                relativeTimeLabel(notification.timestamp),
                color = tokens.neutrals.textTertiary.toColor(),
                fontSize = 12.5.sp,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
        val actionLabel = notification.actionLabel
        val actionRoute = notification.actionRoute
        if (actionLabel != null && actionRoute != null) {
            Text(
                actionLabel,
                color = tokens.neutrals.textPrimary.toColor(),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { onAction(actionRoute) },
            )
        }
    }
}

/**
 * Compact relative time label matching the board's refresh timestamp convention: "now", "5m",
 * "2h", "3d", "2w". Shared by the notification center and action history screens.
 * [nowMillis] is injectable for tests; production callers use the default.
 */
internal fun relativeTimeLabel(
    timestampMillis: Long,
    nowMillis: Long = System.currentTimeMillis(),
): String {
    val diffMs = (nowMillis - timestampMillis).coerceAtLeast(0L)
    val minutes = diffMs / 60_000L
    val hours = diffMs / 3_600_000L
    val days = diffMs / 86_400_000L
    return when {
        minutes < 1L -> "now"
        minutes < 60L -> "${minutes}m"
        hours < 24L -> "${hours}h"
        days < 7L -> "${days}d"
        else -> "${days / 7L}w"
    }
}
