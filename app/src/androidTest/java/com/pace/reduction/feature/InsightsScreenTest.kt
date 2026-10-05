package com.pace.reduction.feature

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pace.reduction.PaceUiState
import com.pace.reduction.core.designsystem.PaceTheme
import com.pace.reduction.domain.PaceInsightsCalculator
import com.pace.reduction.domain.model.*
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InsightsScreenTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val zone = ZoneId.systemDefault()
    private val today = LocalDate.of(2026, 9, 8)

    private fun fixture(): PaceUiState {
        val now = today.atTime(16, 0).atZone(zone).toInstant()
        val plans = (1..60).map { DailyPlanSnapshot(today.minusDays(it.toLong()), 12, 8, 90, 420, 30) }
        val logs = (1..60).flatMap { offset ->
            val count = if (offset <= 7) listOf(4, 6, 5, 7, 4, 6, 5)[offset - 1] else 8 + offset % 3
            (0 until count).map { index ->
                val instant = today.minusDays(offset.toLong()).atTime(8 + index, 15).atZone(zone).toInstant()
                CigaretteLog("$offset-$index", instant, instant, "TEST", null)
            }
        }
        val sessions = (1..10).map { index ->
            val instant = today.minusDays((index % 6 + 1).toLong()).atTime(15, 30).atZone(zone).toInstant()
            UrgeSession("session-$index", instant, instant.plusSeconds(180), if (index % 2 == 0) "BREATH" else "PAUSE",
                4, 2, if (index % 3 == 0) setOf("coffee") else setOf("stress"), null, true, false, null)
        }
        return PaceUiState(settings = PlanSettings(onboardingCompleted = true, baselinePerDay = 12, dailyCeiling = 8,
            pricePerPack = 60.0, cigarettesPerPack = 20, rewardName = "A weekend away", rewardTarget = 1500.0),
            activeLogs = logs, dailySnapshots = plans, urgeSessions = sessions, now = now, loading = false)
    }

    private fun show(state: PaceUiState = fixture(), dark: Boolean = false, scale: Float = 1f,
        onDay: (LocalDate) -> Unit = {}, onCheckIn: () -> Unit = {}, onPlan: () -> Unit = {}) {
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, scale)) {
                PaceTheme(darkTheme = dark, motionLevel = MotionLevel.NONE) {
                    Scaffold { padding ->
                        Surface(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
                            InsightsScreen(state, {}, {}, onDay, onPlan, {}, onCheckIn, { _, _ -> })
                        }
                    }
                }
            }
        }
    }

    private fun screenshot(name: String) {
        rule.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val output = File(instrumentation.targetContext.getExternalFilesDir(null), "insights-qa").apply { mkdirs() }
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(output, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    @Test fun emptyHistoryHasHonestOnboarding() {
        show(PaceUiState(now = fixture().now, loading = false))
        rule.onNodeWithTag("insights_list").performScrollToNode(hasText("Every story starts somewhere."))
        rule.onNodeWithText("Every story starts somewhere.").assertIsDisplayed()
        rule.onNodeWithText("Add past entries").assertExists()
        screenshot("empty-history")
    }

    @Test fun rangesRecomputeAndRetainSelectedDay() {
        var opened: LocalDate? = null
        show(onDay = { opened = it })
        rule.onNodeWithText("7 of 7 days recorded").assertIsDisplayed()
        rule.onNodeWithText("30 days").performClick()
        rule.onNodeWithText("30 of 30 days recorded").assertIsDisplayed()
        rule.onNodeWithTag("insights_list").performScrollToNode(hasText("Open timeline"))
        rule.onNodeWithContentDescription("Previous day").performClick()
        rule.onNodeWithText("Open timeline").performClick()
        rule.runOnIdle { assertEquals(today.minusDays(2), opened) }
        screenshot("daily-drilldown")
    }

    @Test fun patternsShowRealToolAndTriggerEvidence() {
        show()
        rule.onNodeWithText("Patterns").performClick()
        rule.onNodeWithText("The rhythm of your week").assertIsDisplayed()
        screenshot("patterns-light")
        rule.onNodeWithTag("insights_list").performScrollToNode(hasText("Know your triggers"))
        rule.onNodeWithText("Stress").assertExists()
        screenshot("triggers")
        rule.onNodeWithTag("insights_list").performScrollToNode(hasText("What you come back to"))
        rule.onAllNodesWithText("Completed: 5").assertCountEquals(2)
    }

    @Test fun emptyTriggersOpenCheckIn() {
        var clicked = false
        show(fixture().copy(urgeSessions = emptyList()), onCheckIn = { clicked = true })
        rule.onNodeWithText("Patterns").performClick()
        rule.onNodeWithTag("insights_list").performScrollToNode(hasText("Check in"))
        rule.onNodeWithText("Check in").performClick()
        rule.runOnIdle { assertTrue(clicked) }
    }

    @Test fun savingsScenarioChangesWithoutMutatingPlan() {
        val state = fixture()
        show(state)
        rule.onNodeWithText("Savings").performClick()
        rule.onNodeWithText("360 DKK").assertExists()
        rule.onNodeWithText("90 days").performClick()
        rule.onNodeWithText("1,080 DKK").assertExists()
        rule.onNodeWithContentDescription("4 fewer per day").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(2f) }
        rule.onNodeWithText("540 DKK").assertExists()
        rule.runOnIdle { assertEquals(8, state.settings.dailyCeiling) }
        screenshot("savings-light")
    }

    @Test fun missingPriceOpensPlan() {
        var clicked = false
        show(fixture().copy(settings = fixture().settings.copy(pricePerPack = 0.0)), onPlan = { clicked = true })
        rule.onNodeWithText("Savings").performClick()
        rule.onNodeWithText("Give your progress a price.").assertIsDisplayed()
        rule.onAllNodesWithText("Edit my plan")[0].performClick()
        rule.runOnIdle { assertTrue(clicked) }
    }

    @Test fun rendersDarkTrendsAndLargeTextPatterns() {
        show(dark = true, scale = 1.5f)
        rule.onNodeWithText("Trends").assertIsDisplayed()
        screenshot("trends-dark-large")
        rule.onNodeWithText("Patterns").performClick()
        rule.onNodeWithText("The rhythm of your week").assertIsDisplayed()
        screenshot("patterns-dark-large")
    }

    @Test fun rendersLightTrends() {
        show()
        rule.onNodeWithText("7 of 7 days recorded").assertIsDisplayed()
        screenshot("trends-light")
    }

    @Test fun checkInSavesSelectedRatingTriggersAndNote() {
        var captured: Triple<Int?, Set<String>, String>? = null
        rule.setContent {
            PaceTheme(motionLevel = MotionLevel.NONE) {
                CheckInSheet({}, { strength, tags, note -> captured = Triple(strength, tags, note); true }, {})
            }
        }
        rule.onNodeWithContentDescription("Urge intensity 4 of 5").performClick()
        rule.onNodeWithText("Stress").performClick()
        rule.onNodeWithText("A note to your future self").performTextInput("Stepped outside for fresh air")
        rule.onNodeWithText("Save check-in").performScrollTo().performClick()
        rule.onNodeWithText("You made a little space.").assertIsDisplayed()
        rule.runOnIdle {
            assertEquals(4, captured?.first)
            assertEquals(setOf("stress"), captured?.second)
            assertEquals("Stepped outside for fresh air", captured?.third)
        }
        screenshot("check-in-saved")
    }

    @Test fun checkInFailureKeepsDraftForRetry() {
        var succeed = false
        rule.setContent {
            PaceTheme(motionLevel = MotionLevel.NONE) { CheckInSheet({}, { _, _, _ -> succeed }, {}) }
        }
        rule.onNodeWithText("Save check-in").performScrollTo().performClick()
        rule.onNodeWithText("Your check-in could not be saved. Please try again.").assertExists()
        rule.runOnIdle { succeed = true }
        rule.onNodeWithText("Save check-in").performScrollTo().performClick()
        rule.onNodeWithText("You made a little space.").assertIsDisplayed()
    }
}
