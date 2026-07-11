package com.ironclinicgym.sift.ui.board

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ironclinicgym.sift.ui.theme.SiftTheme
import com.ironclinicgym.sift.ui.theme.toColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val COUNTDOWN_MS = 5000
private const val EXIT_ANIM_DURATION_MS = 220

/**
 * Bar shown after a successful save, inviting the next capture.
 *
 * Behavior:
 * - "What else needs doing?" with Task and Dump action chips.
 * - A 5 second countdown fill; when it completes the bar folds away.
 * - Dragging the bar in any direction past a small threshold dismisses it immediately.
 * - Tapping anywhere (other than the Dump chip) opens a fresh add-task drawer right away,
 *   no fold animation needed since the add sheet takes over the screen.
 * - When dismissed (timeout or swipe), a fold-toward-the-FAB exit plays before the bar
 *   is actually removed, then [onDismissed] fires so the caller can clear its state.
 */
@Composable
internal fun PostSaveCaptureBar(
    onTapTask: () -> Unit,
    onTapDump: () -> Unit,
    onDismissed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = SiftTheme.tokens
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    var visible by remember { mutableStateOf(true) }
    val progress = remember { Animatable(0f) }
    val dragOffset = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    val dismissThresholdPx = remember(density) { with(density) { 40.dp.toPx() } }

    // Drive the countdown fill; when it completes, fold the bar away. Using a single
    // Animatable guarantees fill completion and dismissal are the same event.
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(COUNTDOWN_MS, easing = LinearEasing))
        visible = false
    }

    // Once the fold-out exit finishes, tell the caller so it can clear its shown state.
    LaunchedEffect(visible) {
        if (!visible) {
            delay(EXIT_ANIM_DURATION_MS.toLong())
            onDismissed()
        }
    }

    AnimatedVisibility(
        visible = visible,
        exit = shrinkOut(
            animationSpec = tween(durationMillis = EXIT_ANIM_DURATION_MS),
            shrinkTowards = Alignment.BottomEnd,
        ) + fadeOut(animationSpec = tween(durationMillis = EXIT_ANIM_DURATION_MS)),
        modifier = modifier,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .offset { IntOffset(dragOffset.value.x.roundToInt(), dragOffset.value.y.roundToInt()) }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDrag = { change, dragAmount ->
                            change.consume()
                            scope.launch { dragOffset.snapTo(dragOffset.value + dragAmount) }
                        },
                        onDragEnd = {
                            if (dragOffset.value.getDistance() > dismissThresholdPx) {
                                visible = false
                            } else {
                                scope.launch { dragOffset.animateTo(Offset.Zero, spring()) }
                            }
                        },
                        onDragCancel = {
                            scope.launch { dragOffset.animateTo(Offset.Zero, spring()) }
                        },
                    )
                }
                .clip(RoundedCornerShape(20.dp))
                .background(tokens.neutrals.surfaceRaised.toColor())
                .border(1.dp, tokens.neutrals.border.toColor(), RoundedCornerShape(20.dp))
                .clickable(onClick = onTapTask),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    "What else needs doing?",
                    color = tokens.neutrals.textTertiary.toColor(),
                    fontSize = 16.sp,
                    modifier = Modifier.weight(1f),
                )
                PostSaveActionChips(onTapTask = onTapTask, onTapDump = onTapDump)
            }
            LinearProgressIndicator(
                progress = { progress.value },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp)),
                color = tokens.neutrals.textPrimary.toColor().copy(alpha = 0.35f),
                trackColor = Color.Transparent,
            )
        }
    }
}

@Composable
private fun PostSaveActionChips(
    onTapTask: () -> Unit,
    onTapDump: () -> Unit,
) {
    val tokens = SiftTheme.tokens
    Row(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(tokens.neutrals.surface.toColor())
            .border(1.dp, tokens.neutrals.border.toColor(), RoundedCornerShape(12.dp))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            "Task",
            color = tokens.neutrals.bg.toColor(),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clip(RoundedCornerShape(9.dp))
                .background(tokens.neutrals.textPrimary.toColor())
                .clickable(onClick = onTapTask)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
        Text(
            "Dump",
            color = tokens.neutrals.textTertiary.toColor(),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clip(RoundedCornerShape(9.dp))
                .clickable(onClick = onTapDump)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}
