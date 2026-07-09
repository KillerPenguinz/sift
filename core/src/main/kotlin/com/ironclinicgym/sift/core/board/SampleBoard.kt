package com.ironclinicgym.sift.core.board

import com.ironclinicgym.sift.core.domain.SiftTask
import com.ironclinicgym.sift.core.mapping.DatabaseMapping

/**
 * Generates a rich set of sample tasks for a mapping, used by the debug sample-data overlay so
 * the dynamic priority layouts (rows, "plus N more", overdue, times, completed group, focused
 * scrolling) can be exercised without depending on what is in the user's real Notion database.
 * Pure and clock-injected; touches no real data.
 */
object SampleBoard {

    // Varied counts per priority so the grid shows small boxes and a big one that overflows.
    private val counts = listOf(2, 10, 4, 1, 6, 3, 7)

    private val titles = listOf(
        "Reply to the lease renewal email",
        "Send the Q3 deck to Priya",
        "Standup with the platform team",
        "Pick up the prescription",
        "Book the dentist",
        "Plan the weekend trip",
        "Review the open pull requests",
        "Renew the gym membership",
        "Draft the onboarding copy",
        "Water the plants",
        "Call the landlord back",
        "Submit the expense report",
        "Read the design systems book",
        "Refactor the export flow",
        "Learn to make fresh pasta",
        "Sketch the app redesign",
        "Schedule the car service",
        "Reply to Sam about the invoice",
        "Prepare the sprint demo",
        "Back up the photos",
    )

    fun generate(mapping: DatabaseMapping, todayIso: String): List<SiftTask> {
        val buckets = mapping.buckets
        val result = mutableListOf<SiftTask>()

        mapping.priorities.forEachIndexed { i, priority ->
            val n = counts[i % counts.size]
            val doneCount = if (n >= 6) 2 else if (n >= 3) 1 else 0
            for (j in 0 until n) {
                // Every sixth item has no bucket, to exercise the neutral unset coding.
                val bucket = if ((i + j) % 6 == 5) null else buckets.getOrNull(j % buckets.size.coerceAtLeast(1))
                val overdue = j == 0 && i % 2 == 0
                val timed = j == 1
                val done = j >= n - doneCount
                val due = when {
                    overdue -> "2000-01-01"
                    timed -> "${todayIso}T09:${"%02d".format((15 + j * 10) % 60)}:00"
                    else -> null
                }
                result += SiftTask(
                    pageId = "sample-$i-$j",
                    mappingId = mapping.id,
                    title = titles[(i * 5 + j) % titles.size],
                    priorityOptionId = priority.optionId,
                    priorityOptionName = priority.optionName,
                    bucketOptionId = bucket?.optionId,
                    bucketOptionName = bucket?.optionName,
                    isDone = done,
                    due = due,
                    notes = null,
                )
            }
        }
        return result
    }
}
