package com.pace.reduction.domain

import com.pace.reduction.feature.formatLifeRegained
import com.pace.reduction.feature.formatSmokeFree
import com.pace.reduction.feature.milestoneWindow
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetFormattingTest {
    @Test
    fun smokeFreeUsesTheLargestSensibleUnit() {
        assertEquals("12m", formatSmokeFree(Duration.ofMinutes(12)))
        assertEquals("3h 5m", formatSmokeFree(Duration.ofMinutes(185)))
        assertEquals("2d 1h", formatSmokeFree(Duration.ofMinutes(2 * 24 * 60 + 60)))
    }

    @Test
    fun negativeDurationsClampToZero() {
        assertEquals("0m", formatSmokeFree(Duration.ofMinutes(-30)))
    }

    @Test
    fun lifeRegainedRollsUpFromMinutesToDays() {
        assertEquals("45 m", formatLifeRegained(45))
        assertEquals("2 h", formatLifeRegained(150))
        assertEquals("1 d", formatLifeRegained(24 * 60 + 10))
    }

    @Test
    fun milestoneWindowsAreSingularAtOne() {
        assertEquals("20 min", milestoneWindow(20))
        assertEquals("12 h", milestoneWindow(12 * 60))
        assertEquals("1 day", milestoneWindow(24 * 60))
        assertEquals("14 days", milestoneWindow(14 * 24 * 60))
        assertEquals("1 month", milestoneWindow(30L * 24 * 60))
        assertEquals("1 year", milestoneWindow(365L * 24 * 60))
        assertEquals("10 years", milestoneWindow(10L * 365 * 24 * 60))
    }
}
