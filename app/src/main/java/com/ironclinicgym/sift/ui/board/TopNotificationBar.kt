package com.ironclinicgym.sift.ui.board

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ironclinicgym.sift.ui.theme.SiftTheme
import com.ironclinicgym.sift.ui.theme.toColor
import kotlinx.coroutines.delay

sealed interface NotificationVariant {
    data class ConfirmPlacement(
        val priorityName: String,
        val priorityIcon: String,
        val accentColor: Color,
        val onChange: () -> Unit,
    ) : NotificationVariant

    data class Reversible(
        val message: String,
        val icon: String,
        val onUndo: () -> Unit,
    ) : NotificationVariant

    data object RefreshSuccess : NotificationVariant

    data class RefreshError(
        val reason: String,
        val accentColor: Color,
        val onRetry: () -> Unit,
    ) : NotificationVariant
}

@Composable
fun TopNotificationBar(
    variant: NotificationVariant,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = SiftTheme.tokens

    LaunchedEffect(variant) {
        delay(4000L)
        onDismiss()
    }

    AnimatedVisibility(
        visible = true,
        enter = slideInVertically { -it },
        exit = slideOutVertically { -it },
        modifier = modifier,
    ) {
        when (variant) {
            is NotificationVariant.ConfirmPlacement -> {
                val accent = variant.accentColor
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
                    Text(
                        "Change?",
                        color = accent,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable(onClick = variant.onChange),
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

            is NotificationVariant.RefreshSuccess -> {
                val successColor = tokens.successAccent.toColor()
                NotificationRow(
                    bg = tokens.neutrals.surfaceRaised.toColor(),
                    borderColor = tokens.neutrals.border.toColor(),
                ) {
                    MaterialSymbol("check_circle", successColor, size = 19.sp, filled = true)
                    Text(
                        buildAnnotatedString {
                            append("Up to date. ")
                            withStyle(SpanStyle(color = tokens.neutrals.textTertiary.toColor(), fontWeight = FontWeight.Medium)) {
                                append("Refreshed just now.")
                            }
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

            is NotificationVariant.RefreshError -> {
                val accent = variant.accentColor
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
        }
    }
}

@Composable
private fun NotificationRow(
    bg: Color,
    borderColor: Color,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
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
