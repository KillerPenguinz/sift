package com.ironclinicgym.sift.ui.board

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.ironclinicgym.sift.ui.common.SiftWordmark
import com.ironclinicgym.sift.ui.theme.Bricolage
import com.ironclinicgym.sift.ui.theme.SiftTheme
import com.ironclinicgym.sift.ui.theme.toColor

@Composable
fun MenuHubScreen(
    viewModel: BoardViewModel,
    onNotifications: () -> Unit,
    onActionHistory: () -> Unit,
    onSettings: () -> Unit,
    onBack: () -> Unit,
) {
    val tokens = SiftTheme.tokens
    val topInset = WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding()
    val unreadCount by viewModel.unreadNotificationCount.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .background(tokens.neutrals.bg.toColor())
            .padding(top = topInset)
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState()),
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
            "Menu",
            fontFamily = Bricolage,
            fontWeight = FontWeight.Bold,
            fontSize = 28.sp,
            color = tokens.neutrals.textPrimary.toColor(),
            modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 24.dp),
        )

        MenuRow(
            icon = "notifications",
            label = "Notifications",
            trailingCount = if (unreadCount > 0) unreadCount else null,
            onClick = onNotifications,
        )
        MenuRow(
            icon = "history",
            label = "Action History",
            onClick = onActionHistory,
        )
        MenuRow(
            icon = "settings",
            label = "Settings",
            onClick = onSettings,
        )
    }
}

@Composable
private fun MenuRow(
    icon: String,
    label: String,
    trailingCount: Int? = null,
    onClick: () -> Unit,
) {
    val tokens = SiftTheme.tokens
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(tokens.neutrals.surface.toColor())
                .border(1.dp, tokens.neutrals.border.toColor(), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            MaterialSymbol(icon, tokens.neutrals.textSecondary.toColor(), size = 22.sp, filled = true)
        }
        Text(
            label,
            color = tokens.neutrals.textPrimary.toColor(),
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        if (trailingCount != null) {
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(tokens.overdueText.toColor())
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    trailingCount.toString(),
                    color = tokens.neutrals.bg.toColor(),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        MaterialSymbol("chevron_right", tokens.neutrals.textTertiary.toColor(), size = 20.sp, filled = false)
    }
}
