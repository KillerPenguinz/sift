package com.ironclinicgym.sift.ui.board

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ironclinicgym.sift.core.domain.recurrence.RecurrenceText
import com.ironclinicgym.sift.ui.theme.SiftTheme
import com.ironclinicgym.sift.ui.theme.toColor
import java.time.DayOfWeek

internal enum class RRuleFrequency(val label: String) {
    DAILY("Daily"),
    WEEKLY("Weekly"),
    MONTHLY("Monthly"),
    YEARLY("Yearly"),
}

private val WEEK_DAYS = listOf(
    DayOfWeek.MONDAY    to "M",
    DayOfWeek.TUESDAY   to "Tu",
    DayOfWeek.WEDNESDAY to "W",
    DayOfWeek.THURSDAY  to "Th",
    DayOfWeek.FRIDAY    to "F",
    DayOfWeek.SATURDAY  to "Sa",
    DayOfWeek.SUNDAY    to "Su",
)

private val DAY_RRULE_KEYS = listOf("MO", "TU", "WE", "TH", "FR", "SA", "SU")

internal fun buildRRule(
    frequency: RRuleFrequency,
    interval: Int,
    days: Set<DayOfWeek>,
): String {
    val parts = mutableListOf<String>()
    parts += "FREQ=${frequency.name}"
    if (interval > 1) parts += "INTERVAL=$interval"
    if (frequency == RRuleFrequency.WEEKLY && days.isNotEmpty()) {
        val byDay = days
            .sortedBy { it.ordinal }
            .map { day -> DAY_RRULE_KEYS[WEEK_DAYS.indexOfFirst { it.first == day }] }
            .joinToString(",")
        parts += "BYDAY=$byDay"
    }
    return parts.joinToString(";")
}

private fun parseFrequency(rrule: String): RRuleFrequency = when {
    rrule.contains("FREQ=DAILY") -> RRuleFrequency.DAILY
    rrule.contains("FREQ=WEEKLY") -> RRuleFrequency.WEEKLY
    rrule.contains("FREQ=MONTHLY") -> RRuleFrequency.MONTHLY
    rrule.contains("FREQ=YEARLY") -> RRuleFrequency.YEARLY
    else -> RRuleFrequency.DAILY
}

private fun parseInterval(rrule: String): Int =
    Regex("INTERVAL=(\\d+)").find(rrule)?.groupValues?.get(1)?.toIntOrNull() ?: 1

private fun parseDays(rrule: String): Set<DayOfWeek> {
    val match = Regex("BYDAY=([A-Z,]+)").find(rrule) ?: return emptySet()
    return match.groupValues[1].split(",").mapNotNull { key ->
        val idx = DAY_RRULE_KEYS.indexOf(key.trim())
        if (idx >= 0) WEEK_DAYS[idx].first else null
    }.toSet()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurrenceBuilderSheet(
    initialRule: String?,
    onConfirm: (rrule: String?, summary: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val tokens = SiftTheme.tokens
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var frequency by remember {
        mutableStateOf(if (initialRule != null) parseFrequency(initialRule) else RRuleFrequency.DAILY)
    }
    var interval by remember {
        mutableIntStateOf(if (initialRule != null) parseInterval(initialRule) else 1)
    }
    var selectedDays by remember {
        mutableStateOf(if (initialRule != null) parseDays(initialRule) else emptySet<DayOfWeek>())
    }
    val rrule by remember {
        derivedStateOf { buildRRule(frequency, interval, selectedDays) }
    }
    val summary by remember {
        derivedStateOf { RecurrenceText.describe(rrule) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = tokens.neutrals.surfaceRaised.toColor(),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 8.dp)
                .navigationBarsPadding(),
        ) {
            // Summary text at top
            Text(
                summary,
                color = tokens.neutrals.textPrimary.toColor(),
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 12.dp),
            )

            HorizontalDivider(color = tokens.neutrals.border.toColor())

            Spacer(Modifier.height(16.dp))

            // Frequency label
            Text(
                "FREQUENCY",
                color = tokens.neutrals.textTertiary.toColor(),
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.04.sp,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RRuleFrequency.entries.forEach { freq ->
                    FilterChip(
                        modifier = Modifier.weight(1f),
                        selected = frequency == freq,
                        onClick = {
                            frequency = freq
                            if (freq != RRuleFrequency.WEEKLY) selectedDays = emptySet()
                        },
                        label = { Text(freq.label, fontSize = 13.sp, maxLines = 1) },
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Interval row
            Text(
                "EVERY",
                color = tokens.neutrals.textTertiary.toColor(),
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.04.sp,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                IconButton(
                    onClick = { if (interval > 1) interval-- },
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .border(1.dp, tokens.neutrals.border.toColor(), CircleShape),
                ) {
                    MaterialSymbol(
                        "remove",
                        if (interval > 1) tokens.neutrals.textPrimary.toColor()
                        else tokens.neutrals.textTertiary.toColor(),
                        size = 18.sp,
                        filled = true,
                    )
                }
                Text(
                    interval.toString(),
                    color = tokens.neutrals.textPrimary.toColor(),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                IconButton(
                    onClick = { if (interval < 99) interval++ },
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .border(1.dp, tokens.neutrals.border.toColor(), CircleShape),
                ) {
                    MaterialSymbol(
                        "add",
                        tokens.neutrals.textPrimary.toColor(),
                        size = 18.sp,
                        filled = true,
                    )
                }
                val unitLabel = when (frequency) {
                    RRuleFrequency.DAILY -> if (interval == 1) "day" else "days"
                    RRuleFrequency.WEEKLY -> if (interval == 1) "week" else "weeks"
                    RRuleFrequency.MONTHLY -> if (interval == 1) "month" else "months"
                    RRuleFrequency.YEARLY -> if (interval == 1) "year" else "years"
                }
                Text(
                    unitLabel,
                    color = tokens.neutrals.textSecondary.toColor(),
                    fontSize = 15.sp,
                )
            }

            // Weekly: day-of-week row
            if (frequency == RRuleFrequency.WEEKLY) {
                Spacer(Modifier.height(16.dp))
                Text(
                    "ON DAYS",
                    color = tokens.neutrals.textTertiary.toColor(),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.04.sp,
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    WEEK_DAYS.forEach { (day, shortLabel) ->
                        val selected = day in selectedDays
                        val bgColor = if (selected) tokens.neutrals.textPrimary.toColor()
                        else tokens.neutrals.surface.toColor()
                        val fgColor = if (selected) tokens.neutrals.bg.toColor()
                        else tokens.neutrals.textSecondary.toColor()
                        Box(
                            Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(bgColor)
                                .border(
                                    1.dp,
                                    if (selected) tokens.neutrals.textPrimary.toColor()
                                    else tokens.neutrals.border.toColor(),
                                    CircleShape,
                                )
                                .clickable {
                                    selectedDays = if (selected) selectedDays - day else selectedDays + day
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                shortLabel,
                                color = fgColor,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            Button(
                onClick = { onConfirm(rrule, summary) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Done", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }

            TextButton(
                onClick = { onConfirm(null, "Does not repeat") },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    "Remove repeat",
                    fontSize = 15.sp,
                    color = tokens.neutrals.textSecondary.toColor(),
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
