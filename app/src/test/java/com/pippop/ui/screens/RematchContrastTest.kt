package com.pippop.ui.screens

import com.pippop.ui.theme.DarkPalette
import com.pippop.ui.theme.LightPalette
import com.pippop.ui.theme.contrast
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Rematch button's words read at 4.5:1 or more on both of its fills, in light and dark. */
class RematchContrastTest {
    @Test
    fun rematchTextReads() {
        for ((name, p) in listOf("light" to LightPalette, "dark" to DarkPalette)) {
            for (ready in listOf(false, true)) {
                val fill = if (ready) p.good else p.primary
                val ratio = contrast(rematchInk(p, ready), fill)
                assertTrue("$name, others ready = $ready: $ratio", ratio >= 4.5f)
            }
        }
    }
}
