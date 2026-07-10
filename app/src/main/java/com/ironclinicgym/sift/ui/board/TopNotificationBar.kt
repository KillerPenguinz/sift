package com.ironclinicgym.sift.ui.board

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ironclinicgym.sift.ui.theme.SiftTheme
import com.ironclinicgym.sift.ui.theme.toColor
import kotlinx.coroutines.delay

sealed interface NotificationVariant {
    data class ConfirmPlacement(
        val priorityName: String,
        val priorityIcon: String,
        val colorKey: String,
    ) : NotificationVariant

    data class Reversible(
        val message: String,
        val icon: String,
        val onUndo: () -> Unit,
    ) : NotificationVariant

    data class RefreshError(
        val reason: String,
        val onRetry: () -> Unit,
    ) : NotificationVariant

    data class Info(val message: String) : NotificationVariant

    data class InflationNudge(
        val message: String,
        val actionLabel: String,
        val onAction: () -> Unit,
    ) : NotificationVariant
}

@Composable
fun TopNotificationBar(
    variant: NotificationVariant,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    timerKey: Any = variant,
    animateVertically: Boolean = true,
) {
    // Auto-dismiss keyed on the queue entry id (falls back to the variant), so each queued
    // notification gets its full display duration even when consecutive variants are equal.
    if (variant !is NotificationVariant.InflationNudge) {
        LaunchedEffect(timerKey) {
            delay(4000L)
            onDismiss()
        }
    }

    // Floating placements slide in vertically on their own; the subheader slot passes
    // animateVertically = false so its horizontal AnimatedContent transition is the only motion.
    if (animateVertically) {
        AnimatedVisibility(
            visible = true,
            enter = slideInVertically { -it },
            exit = slideOutVertically { -it },
            modifier = modifier,
        ) {
            NotificationBarContent(variant, onDismiss)
        }
    } else {
        Box(modifier) {
            NotificationBarContent(variant, onDismiss)
        }
    }
}

@Composable
private fun NotificationBarContent(
    variant: NotificationVariant,
    onDismiss: () -> Unit,
) {
    val tokens = SiftTheme.tokens
    when (variant) {
        is NotificationVariant.ConfirmPlacement -> {
            val accent = priorityColorsOf(variant.colorKey).accent.toColor()
            NotificationRow(
                bg = accent.copy(alpha = 0.16f),
                borderColor = accent.copy(alpha = 0.30f),
            ) {
                MaterialSymbol(variant.priorityIcon, accent, size = 19.sp, filled = true)
                Text(
                    buildAnnotatedString {
                        append("Added to ")
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = accent)) {
                            append(variant.priorityName)
                        }
                        append(".")
                    },
                    color = tokens.neutrals.textPrimary.toColor(),
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
                MaterialSymbol(
                    "close",
                    tokens.neutrals.textTertiary.toColor(),
                    size = 19.sp,
                    filled = false,
                    modifier = Modifier.clickable(onClick = onDismiss),
                )
            }
        }

        is NotificationVariant.Reversible -> {
            NotificationRow(
                bg = tokens.neutrals.surfaceRaised.toColor(),
                borderColor = tokens.neutrals.border.toColor(),
            ) {
                MaterialSymbol(variant.icon, tokens.neutrals.textSecondary.toColor(), size = 19.sp, filled = false)
                Text(
                    variant.message,
                    color = tokens.neutrals.textPrimary.toColor(),
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "Undo",
                    color = tokens.neutrals.textPrimary.toColor(),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable(onClick = variant.onUndo),
                )
                MaterialSymbol(
                    "close",
                    tokens.neutrals.textTertiary.toColor(),
                    size = 19.sp,
                    filled = false,
                    modifier = Modifier.clickable(onClick = onDismiss),
                )
            }
        }

        is NotificationVariant.RefreshError -> {
            val accent = tokens.overdueText.toColor()
            NotificationRow(
                bg = accent.copy(alpha = 0.16f),
                borderColor = accent.copy(alpha = 0.32f),
            ) {
                MaterialSymbol("cloud_off", accent, size = 19.sp, filled = true)
                Text(
                    buildAnnotatedString {
                        append("Couldn't sync ")
                        withStyle(SpanStyle(color = tokens.neutrals.textTertiary.toColor(), fontWeight = FontWeight.Medium)) {
                            append(variant.reason)
                        }
                    },
                    color = tokens.neutrals.textPrimary.toColor(),
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "Retry",
                    color = accent,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable(onClick = variant.onRetry),
                )
                MaterialSymbol(
                    "close",
                    tokens.neutrals.textTertiary.toColor(),
                    size = 19.sp,
                    filled = false,
                    modifier = Modifier.clickable(onClick = onDismiss),
                )
            }
        }

        is NotificationVariant.Info -> {
            NotificationRow(
                bg = tokens.neutrals.surfaceRaised.toColor(),
                borderColor = tokens.neutrals.border.toColor(),
            ) {
                Text(
                    variant.message,
                    color = tokens.neutrals.textPrimary.toColor(),
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
                MaterialSymbol(
                    "close",
                    tokens.neutrals.textTertiary.toColor(),
                    size = 19.sp,
                    filled = false,
                    modifier = Modifier.clickable(onClick = onDismiss),
                )
            }
        }

        is NotificationVariant.InflationNudge -> {
            NotificationRow(
                bg = tokens.neutrals.surfaceRaised.toColor(),
                borderColor = tokens.neutrals.border.toColor(),
            ) {
                Text(
                    variant.message,
                    color = tokens.neutrals.textPrimary.toColor(),
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    variant.actionLabel,
                    color = tokens.neutrals.textPrimary.toColor(),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable(onClick = { variant.onAction(); onDismiss() }),
                )
                MaterialSymbol(
                    "close",
                    tokens.neutrals.textTertiary.toColor(),
                    size = 19.sp,
                    filled = false,
                    modifier = Modifier.clickable(onClick = onDismiss),
                )
            }
        }
    }
}

@Composable
private fun NotificationRow(
    bg: Color,
    borderColor: Color,
    content: @Composable RowScope.() -> Unit,
) {
    // No outer margin here: the subheader slot supplies its own screen padding so the band
    // aligns with the title it replaces; floating call sites add their own margin.
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
            .padding(horizontal = 15.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        content()
    }
}

/**
 * Fixed height of the subheader slot: the natural height of the notification band (12dp vertical
 * padding each side plus one line of 19sp icon/14.5sp text), the taller of the slot's two states.
 * Keeping it constant means the layout never jumps when a notification replaces the title.
 */
internal val SUBHEADER_SLOT_HEIGHT = 48.dp

/**
 * The subheader slot shared by the Board and Brain dump screens: shows [idleContent] (the
 * screen's title row) when nothing is queued, or the current notification. The undo bar takes
 * priority over the queue. Fixed height (see [SUBHEADER_SLOT_HEIGHT]) so the content below
 * never shifts; the title slides out vertically while notifications slide through horizontally.
 */
@Composable
internal fun SubheaderSlot(
    viewModel: BoardViewModel,
    bottomPadding: Dp,
    idleContent: @Composable () -> Unit,
) {
    val undoToken by viewModel.undoToken.collectAsStateWithLifecycle()
    val currentQueuedNotification by viewModel.currentNotification.collectAsStateWithLifecycle()
    val displayedNotification = undoToken?.let { token ->
        BoardViewModel.QueuedNotification(
            id = BoardViewModel.UNDO_BAR_ID,
            variant = NotificationVariant.Reversible(
                message = token.label,
                icon = "swap_horiz",
                onUndo = { viewModel.undo() },
            ),
        )
    } ?: currentQueuedNotification

    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, bottom = bottomPadding)
            .height(SUBHEADER_SLOT_HEIGHT),
        contentAlignment = Alignment.CenterStart,
    ) {
        AnimatedVisibility(
            visible = displayedNotification == null,
            enter = slideInVertically(tween(200)) { -it } + fadeIn(tween(200)),
            exit = slideOutVertically(tween(200)) { -it } + fadeOut(tween(200)),
        ) {
            idleContent()
        }
        AnimatedContent(
            targetState = displayedNotification,
            contentKey = { it?.id },
            transitionSpec = {
                (slideInHorizontally(tween(250)) { it } + fadeIn(tween(250))) togetherWith
                    (slideOutHorizontally(tween(250)) { -it } + fadeOut(tween(250)))
            },
            label = "subheaderNotificationSlot",
        ) { entry ->
            entry?.let { (entryId, variant) ->
                TopNotificationBar(
                    variant = variant,
                    timerKey = entryId,
                    animateVertically = false,
                    onDismiss = {
                        if (entryId == BoardViewModel.UNDO_BAR_ID) viewModel.dismissUndo()
                        else {
                            viewModel.dismissNotification(entryId)
                            if (variant is NotificationVariant.InflationNudge) {
                                viewModel.dismissInflationAlert()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
@Deprecated("Use the variant-based TopNotificationBar instead")
fun TopNotificationBar(
    message: String,
    onUndo: (() -> Unit)?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (onUndo != null) {
        TopNotificationBar(
            variant = NotificationVariant.Reversible(
                message = message,
                icon = "swap_horiz",
                onUndo = onUndo,
            ),
            onDismiss = onDismiss,
            modifier = modifier,
        )
    }
}
