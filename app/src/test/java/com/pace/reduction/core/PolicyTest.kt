package com.pace.reduction.core

import com.pace.reduction.core.network.SafeLinks
import com.pace.reduction.core.notifications.NotificationPolicy
import com.pace.reduction.domain.WidgetTapGuard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PolicyTest {
    @Test
    fun urlAllowlistRequiresHttpsAndExactHost() {
        assertTrue(SafeLinks.isAllowed("https://open-meteo.com/"))
        assertTrue(SafeLinks.isAllowed("https://ollama.com/settings/keys"))
        assertFalse(SafeLinks.isAllowed("http://open-meteo.com/"))
        assertFalse(SafeLinks.isAllowed("https://open-meteo.com.evil.example/"))
        assertFalse(SafeLinks.isAllowed("https://ollama.com.evil.example/"))
        assertFalse(SafeLinks.isAllowed("javascript:alert(1)"))
    }

    @Test
    fun notificationPolicySuppressesQuietRateLimitedDismissedAndRecentLog() {
        val now = 20_000_000L
        fun eligible(
            quiet: Boolean = false,
            count: Int = 0,
            lastNotification: Long = 0,
            dismissed: Long = 0,
            latestLog: Long = 0,
        ) = NotificationPolicy.isEligible(now, quiet, count, 2, lastNotification, dismissed, latestLog)

        assertTrue(eligible())
        assertFalse(eligible(quiet = true))
        assertFalse(eligible(count = 2))
        assertFalse(eligible(lastNotification = now - 60_000))
        assertFalse(eligible(dismissed = now + 1))
        assertFalse(eligible(latestLog = now - 60_000))
    }

    @Test
    fun widgetTapGuardBlocksOnlyRapidWidgetDuplicates() {
        assertTrue(WidgetTapGuard.shouldReuseLatest("WIDGET", 10_000, 11_499))
        assertFalse(WidgetTapGuard.shouldReuseLatest("WIDGET", 10_000, 11_500))
        assertFalse(WidgetTapGuard.shouldReuseLatest("APP", 10_000, 10_100))
        assertFalse(WidgetTapGuard.shouldReuseLatest(null, null, 10_100))
    }
}
