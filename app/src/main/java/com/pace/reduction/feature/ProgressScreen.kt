package com.pace.reduction.feature

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pace.reduction.PaceUiState
import com.pace.reduction.R
import com.pace.reduction.domain.BadgeCatalogue
import com.pace.reduction.domain.CalendarDay
import com.pace.reduction.domain.CalendarHistory
import com.pace.reduction.domain.DayStanding
import com.pace.reduction.domain.ReductionPlanner
import com.pace.reduction.domain.WeeklyReview
import com.pace.reduction.core.designsystem.entrance
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

/**
 * The history screen, grouped rather than stacked.
 *
 * It used to be ten cards in a column with nothing to say which mattered: a bar chart of the last
 * week, then body, then patterns, then badges, all at the same weight. Now it answers three
 * questions in the order people ask them — how is this week going, what does the run of weeks look
 * like, and what has my own history taught the app — with headers that let you skip to the one you
 * came for.
 */
@Composable
internal fun ProgressScreen(
    uiState: PaceUiState,
    onOpenSettings: () -> Unit,
    onOpenLedger: () -> Unit,
    onToggleSteps: (Boolean) -> Unit,
) {
    val metrics = requireNotNull(uiState.progress)
    val quit = uiState.quit
    val locale = LocalConfiguration.current.locales[0]
    val triggerSample = uiState.urgeSessions.filter { it.triggerTags.isNotEmpty() }
    val topTrigger = triggerSample.flatMap { it.triggerTags }
        .groupingBy { it }
        .eachCount()
        .maxByOrNull { it.value }
    val suggestedCeiling = ReductionPlanner.suggestedCeiling(
        currentCeiling = uiState.settings.dailyCeiling,
        step = uiState.settings.reductionStep,
        reviewIntervalDays = uiState.settings.reviewIntervalDays,
        completedDays = metrics.days,
    )

    PaceScreen(
        title = stringResource(R.string.progress_title),
        actions = {
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.settings_title))
            }
        },
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { LeadParagraph(stringResource(R.string.progress_intro)) }
        if (quit != null) {
            item {
                StatGrid(
                    listOf(
                        quit.zeroDayStreak.toString() to stringResource(R.string.quit_streak),
                        metrics.avoidedCigarettes.toString() to stringResource(R.string.estimated_avoided),
                        String.format(
                            locale,
                            "%.0f %s",
                            metrics.estimatedSavings,
                            uiState.settings.currencyCode,
                        ) to stringResource(R.string.estimated_savings),
                    ),
                    modifier = Modifier.entrance(0),
                )
            }
        }

        uiState.weeklyReview?.let { review ->
            item { SectionHeader(stringResource(R.string.section_this_week)) }
            item {
                WeekReviewCard(
                    review = review,
                    currencyCode = uiState.settings.currencyCode,
                    locale = locale,
                    modifier = Modifier.entrance(1),
                )
            }
        }

        item { SectionHeader(stringResource(R.string.section_history)) }
        uiState.calendar?.let { calendar ->
            item { HistoryCalendarCard(calendar, locale, modifier = Modifier.entrance(2)) }
        }
        uiState.habitTrend?.let { trend ->
            item {
                HabitTrendCard(
                    trend = trend,
                    settings = uiState.settings,
                    onOpenLedger = onOpenLedger,
                    modifier = Modifier.entrance(3),
                )
            }
        }
        item {
            // A week of bars beside the seven- and thirty-day averages the card already carries;
            // thirty rows of them was a scroll, not a chart.
            StepSection(steps = uiState.steps, rangeDays = 7, onToggle = onToggleSteps)
        }

        val withdrawal = uiState.withdrawal?.takeIf { it.relevant }
        if (withdrawal != null || quit != null) {
            item { SectionHeader(stringResource(R.string.section_body)) }
        }
        // Above the recovery ladder deliberately: what the next three days cost is the more urgent
        // of the two questions, and it is the one nobody answers.
        withdrawal?.let { item { WithdrawalCard(it) } }
        if (quit != null) {
            item {
                SectionCard {
                    Text(stringResource(R.string.quit_health_title), style = MaterialTheme.typography.titleLarge)
                    Text(
                        stringResource(R.string.quit_best_streak, quit.bestZeroDayStreak),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    RecoveryTimeline(quit)
                }
            }
        }

        item { SectionHeader(stringResource(R.string.section_patterns)) }
        item {
            SectionCard {
                Text(stringResource(R.string.progress_evidence), style = MaterialTheme.typography.titleLarge)
                // Five sentences became five figures: these are numbers to compare against
                // yesterday's, and a paragraph is the wrong shape for that.
                StatGrid(
                    listOf(
                        String.format(locale, "%.1f", metrics.sevenDayAverage ?: 0.0) to
                            stringResource(R.string.average_seven_label),
                        durationText(metrics.longestGapMinutes) to stringResource(R.string.week_longest_gap),
                        durationText(metrics.bestMorningHoldMinutes) to
                            stringResource(R.string.best_morning_hold_label),
                        "${metrics.steadyDays7} · ${metrics.steadyDays30}" to
                            stringResource(R.string.steady_days_label),
                    ),
                )
                Text(
                    stringResource(R.string.pauses_recorded, metrics.pausesCompleted),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    stringResource(R.string.delay_is_win),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        // The three things the app has worked out on its own, in one card rather than three
        // identical ones — they are all the same kind of claim.
        val window = uiState.urgePattern.window
        if (window != null || topTrigger != null || suggestedCeiling != null) {
            item {
                SectionCard {
                    window?.let {
                        Text(stringResource(R.string.pattern_window_title), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(
                                R.string.pattern_window_body,
                                formatMinutes(it.startMinutes),
                                formatMinutes(it.endMinutes),
                                (it.share * 100).toInt(),
                                uiState.urgePattern.sampleSize,
                                uiState.urgePattern.daysCovered,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    if (topTrigger != null) {
                        if (window != null) HorizontalDivider(Modifier.padding(vertical = 4.dp))
                        Text(stringResource(R.string.pattern_reflection), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(
                                R.string.trigger_reflection,
                                topTrigger.key.replace('_', ' '),
                                topTrigger.value,
                                triggerSample.size,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    if (suggestedCeiling != null) {
                        if (window != null || topTrigger != null) {
                            HorizontalDivider(Modifier.padding(vertical = 4.dp))
                        }
                        Text(
                            stringResource(R.string.review_suggestion_title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            stringResource(R.string.review_suggestion_body, suggestedCeiling),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }

        item { SectionHeader(stringResource(R.string.section_milestones)) }
        if (uiState.settings.rewardTarget > 0) {
            item {
                SectionCard {
                    Text(stringResource(R.string.reward_progress_title), style = MaterialTheme.typography.titleLarge)
                    LinearProgressIndicator(
                        progress = { metrics.rewardProgress.toFloat() },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        stringResource(
                            R.string.reward_progress_value,
                            (metrics.rewardProgress * 100).toInt(),
                            uiState.settings.rewardName.ifBlank { stringResource(R.string.your_reward) },
                        ),
                    )
                }
            }
        }
        item {
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.badges_title),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        stringResource(R.string.badges_earned, uiState.achievements.size, BadgeCatalogue.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(4.dp))
                BadgeFamilyList(uiState.achievements)
            }
        }
    }
}

/**
 * This week against the same days of last week.
 *
 * The deltas are the content; the raw figures are there so the deltas can be checked. Fewer is
 * drawn as progress and more is drawn plainly rather than in alarm red — a bad week is information,
 * and a screen that shouts at you for it is a screen you stop opening.
 */
@Composable
private fun WeekReviewCard(
    review: WeeklyReview,
    currencyCode: String,
    locale: Locale,
    modifier: Modifier = Modifier,
) {
    SectionCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text(
                    review.thisWeek.logged.toString(),
                    style = MaterialTheme.typography.displaySmall,
                )
                Text(
                    stringResource(R.string.week_logged),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            review.loggedDelta?.takeIf { review.comparable }?.let { delta ->
                DeltaPill(
                    text = when {
                        delta < 0 -> stringResource(R.string.week_fewer, -delta)
                        delta > 0 -> stringResource(R.string.week_more, delta)
                        else -> stringResource(R.string.week_level)
                    },
                    good = delta <= 0,
                )
            }
        }

        HorizontalDivider(Modifier.padding(vertical = 4.dp))

        WeekRow(
            label = stringResource(R.string.week_clear_days),
            value = review.thisWeek.clearDays.toString(),
            delta = review.clearDaysDelta?.takeIf { review.comparable }?.let { delta ->
                when {
                    delta > 0 -> stringResource(R.string.week_up, delta) to true
                    delta < 0 -> stringResource(R.string.week_down, -delta) to false
                    else -> null
                }
            },
        )
        WeekRow(
            label = stringResource(R.string.week_longest_gap),
            value = durationText(review.thisWeek.longestGapMinutes),
            delta = review.longestGapDelta?.takeIf { review.comparable && it != 0L }?.let { delta ->
                if (delta > 0) {
                    stringResource(R.string.week_gap_longer, durationText(delta)) to true
                } else {
                    stringResource(R.string.week_gap_shorter, durationText(-delta)) to false
                }
            },
        )
        if (review.thisWeek.moneySaved.signum() > 0) {
            WeekRow(
                label = stringResource(R.string.week_kept),
                value = String.format(locale, "%.2f %s", review.thisWeek.moneySaved, currencyCode),
                delta = null,
            )
        }

        Text(
            when {
                !review.comparable -> stringResource(R.string.week_no_comparison)
                review.partial -> stringResource(R.string.week_compare_partial, review.thisWeek.daysCounted)
                else -> stringResource(R.string.week_compare_full)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun WeekRow(label: String, value: String, delta: Pair<String, Boolean>?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.titleMedium)
        delta?.let { (text, good) ->
            Spacer(Modifier.width(8.dp))
            DeltaPill(text = text, good = good)
        }
    }
}

@Composable
private fun DeltaPill(text: String, good: Boolean) {
    val container = if (good) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = if (good) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(container)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/**
 * A quarter of history as weeks under each other.
 *
 * Rows are weeks and columns are weekdays, which is the arrangement that makes a bad Friday visible
 * as a column rather than as four separate bars a month apart.
 */
@Composable
private fun HistoryCalendarCard(
    calendar: CalendarHistory,
    locale: Locale,
    modifier: Modifier = Modifier,
) {
    SectionCard(modifier = modifier) {
        Text(
            stringResource(R.string.calendar_title, calendar.weeks.size),
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            stringResource(R.string.calendar_counts_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (calendar.isEmpty) {
            Text(
                stringResource(R.string.calendar_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@SectionCard
        }

        val weekdays = (0 until 7).map { offset ->
            calendar.firstDayOfWeek.plus(offset.toLong())
                .getDisplayName(TextStyle.NARROW, locale)
        }
        Row(
            modifier = Modifier.fillMaxWidth().clearAndSetSemantics { },
            horizontalArrangement = Arrangement.spacedBy(CellGap, Alignment.CenterHorizontally),
        ) {
            // The month label column, kept the same width as the labels below it so the grid lines
            // up with its own heading row.
            Spacer(Modifier.width(MonthLabelWidth))
            weekdays.forEach { day ->
                Text(
                    day,
                    modifier = Modifier.width(CellSize),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        val monthFormatter = remember(locale) { DateTimeFormatter.ofPattern("LLL", locale) }
        calendar.weeks.forEachIndexed { index, week ->
            // A month is named on the first row that contains one of its days, so the left edge
            // reads as a running date rather than repeating the same three letters thirteen times.
            val seenBefore = calendar.weeks.take(index)
                .flatMap { it.days.filterNotNull() }
                .map { it.date.month }
                .toSet()
            val opening = week.days.filterNotNull().firstOrNull { it.date.month !in seenBefore }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(CellGap, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = opening?.let { monthFormatter.format(it.date) }.orEmpty(),
                    modifier = Modifier.width(MonthLabelWidth),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                week.days.forEach { day ->
                    CalendarCell(day = day, locale = locale)
                }
            }
        }

        Spacer(Modifier.height(2.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LegendKey(DayStanding.CLEAR, stringResource(R.string.calendar_legend_clear))
            LegendKey(DayStanding.UNDER, stringResource(R.string.calendar_legend_under))
            LegendKey(DayStanding.AT, stringResource(R.string.calendar_legend_at))
            LegendKey(DayStanding.OVER, stringResource(R.string.calendar_legend_over))
        }
        Text(
            stringResource(R.string.calendar_summary, calendar.steadyDays, calendar.recordedDays),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Fixed rather than a share of the width: a quarter is thirteen rows, and squares big enough to
 * fill a phone would make the grid the whole screen instead of a glance.
 */
private val CellSize = 28.dp
private val CellGap = 5.dp
private val MonthLabelWidth = 30.dp

@Composable
private fun CalendarCell(day: CalendarDay?, locale: Locale, modifier: Modifier = Modifier) {
    val dateFormatter = remember(locale) {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
    }
    val description = when {
        day == null || day.future || day.standing == DayStanding.UNKNOWN ->
            day?.let { stringResource(R.string.calendar_day_unknown, dateFormatter.format(it.date)) }
        day.standing == DayStanding.CLEAR ->
            stringResource(R.string.calendar_day_clear, dateFormatter.format(day.date))
        day.ceiling == null ->
            stringResource(R.string.calendar_day_unplanned, dateFormatter.format(day.date), day.count)
        else -> stringResource(
            R.string.calendar_day_counted,
            dateFormatter.format(day.date),
            day.count,
            day.ceiling,
        )
    }
    Box(
        modifier = modifier
            .size(CellSize)
            .clip(RoundedCornerShape(5.dp))
            .background(standingColour(day?.standing, future = day?.future ?: true))
            .then(
                if (description == null) {
                    Modifier
                } else {
                    Modifier.semantics { contentDescription = description }
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (day != null && !day.future && day.standing != DayStanding.UNKNOWN) {
            Text(
                text = day.count.toString(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = standingContentColour(day.standing),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun LegendKey(standing: DayStanding, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(standingColour(standing, future = false)),
        )
        Spacer(Modifier.width(5.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * One ramp from "watched and clear" to "over", plus a near-invisible tile for days with no data.
 *
 * Clear days are the strongest colour rather than the palest: the grid should reward the days that
 * went well, and a heatmap where the worst days are the loudest turns a history into a rap sheet.
 */
@Composable
private fun standingColour(standing: DayStanding?, future: Boolean): Color {
    val scheme = MaterialTheme.colorScheme
    return when {
        standing == null || future -> scheme.surfaceVariant.copy(alpha = 0.25f)
        else -> when (standing) {
            DayStanding.UNKNOWN -> scheme.surfaceVariant.copy(alpha = 0.45f)
            DayStanding.CLEAR -> scheme.primary
            DayStanding.UNDER -> scheme.primaryContainer
            DayStanding.AT -> scheme.tertiaryContainer
            DayStanding.OVER -> scheme.errorContainer
        }
    }
}

@Composable
private fun standingContentColour(standing: DayStanding): Color {
    val scheme = MaterialTheme.colorScheme
    return when (standing) {
        DayStanding.UNKNOWN -> scheme.onSurfaceVariant
        DayStanding.CLEAR -> scheme.onPrimary
        DayStanding.UNDER -> scheme.onPrimaryContainer
        DayStanding.AT -> scheme.onTertiaryContainer
        DayStanding.OVER -> scheme.onErrorContainer
    }
}
