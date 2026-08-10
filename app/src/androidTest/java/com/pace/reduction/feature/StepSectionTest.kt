package com.pace.reduction.feature

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pace.reduction.core.designsystem.PaceTheme
import com.pace.reduction.domain.StepCalculator
import com.pace.reduction.domain.StepDay
import com.pace.reduction.domain.StepMetrics
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Covers the states the emulator's hardware cannot produce.
 *
 * No Android emulator image exposes `TYPE_STEP_COUNTER`, so the populated card is unreachable by
 * walking the app by hand; rendering it from a fabricated [StepMetrics] is the only way to see that
 * the figures land where they should.
 */
@RunWith(AndroidJUnit4::class)
class StepSectionTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val today = LocalDate.now()

    private fun day(daysAgo: Long, steps: Long) = StepDay(
        date = today.minusDays(daysAgo),
        steps = steps,
        distanceKm = StepCalculator.distanceKm(steps, 170),
    )

    private fun show(metrics: StepMetrics) {
        rule.setContent {
            PaceTheme {
                StepSection(steps = metrics, rangeDays = 7, onToggle = {})
            }
        }
    }

    @Test
    fun aDeviceWithoutAPedometerSaysSoAndOffersNoToggle() {
        show(StepMetrics(available = false))
        rule.onNodeWithText("This device has no step sensor.").assertIsDisplayed()
    }

    @Test
    fun withPermissionMissingItAsksRatherThanShowingEmptyFigures() {
        show(StepMetrics(available = true, enabled = true, permissionGranted = false))
        rule.onNodeWithText("Allow step counting").assertIsDisplayed()
    }

    @Test
    fun anEnabledButEmptyHistoryExplainsItselfInsteadOfShowingZeroes() {
        show(StepMetrics(available = true, enabled = true, permissionGranted = true))
        rule.onNodeWithText("No steps counted yet. Take your phone with you and check back.")
            .assertIsDisplayed()
    }

    @Test
    fun aPopulatedHistoryShowsTodayTheAveragesAndTheBestDay() {
        val metrics = StepCalculator.calculate(
            today = today,
            days = listOf(day(2, 12_000), day(1, 6_000), day(0, 8_432)),
            available = true,
            permissionGranted = true,
            enabled = true,
        )
        show(metrics)

        // Today's figure, grouped rather than a bare run of digits.
        rule.onNodeWithText("8,432").assertIsDisplayed()
        rule.onNodeWithText("Steps today").assertIsDisplayed()
        rule.onNodeWithText("7-day average").assertIsDisplayed()
        rule.onNodeWithText("30-day average").assertIsDisplayed()
        // (12000 + 6000 + 8432) / 3 = 8810 to the nearest whole step. Both windows cover all three
        // days here, so the same figure is expected to appear under each of them.
        rule.onAllNodesWithText("8,810").assertCountEquals(2)
        // 8432 steps at a 170cm stride (0.7038 m) is a little over 5.9km.
        rule.onNodeWithText("5.93 km").assertIsDisplayed()
        // Exists rather than displayed: the card is taller than the bare test container, and this
        // line sits below its fold. In the app it lives in a scrolling list.
        rule.onNodeWithText("Best day so far: 12,000 (8.45 km)").assertExists()
    }
}
