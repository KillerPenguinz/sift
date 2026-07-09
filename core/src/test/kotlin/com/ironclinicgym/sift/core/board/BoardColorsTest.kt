package com.ironclinicgym.sift.core.board

import com.ironclinicgym.sift.core.theme.PriorityKey
import com.ironclinicgym.sift.core.theme.DEFAULT_THEME
import com.ironclinicgym.sift.core.theme.BucketKey
import com.ironclinicgym.sift.core.theme.THEMES
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BoardColorsTest {

    @Test fun `priority colors resolve for every accent and theme`() {
        THEMES.values.forEach { theme ->
            PriorityKey.entries.forEach { key -> priorityColors(theme, key) }
        }
    }

    @Test fun `asap priority header is a light wash in paper, not the raw accent`() {
        val colors = priorityColors(DEFAULT_THEME, PriorityKey.ASAP)
        assertNotEquals(colors.accent, colors.headerBg)
    }

    @Test fun `unset bucket uses a neutral coding distinct from a real bucket`() {
        val neutral = bucketColors(DEFAULT_THEME, null)
        val work = bucketColors(DEFAULT_THEME, BucketKey.WORK)
        assertNotEquals(work.color, neutral.color)
    }

    @Test fun `light theme darkens accent text below the accent`() {
        val c = priorityColors(DEFAULT_THEME, PriorityKey.ASAP)
        assertTrue(c.accentText.r < c.accent.r)
    }
}
