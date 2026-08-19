package com.pace.reduction.widget

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.DpSize
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.ColorFilter
import androidx.glance.ImageProvider
import androidx.glance.Image
import androidx.glance.LocalContext
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.pace.reduction.MainActivity
import com.pace.reduction.PaceApplication
import com.pace.reduction.R
import com.pace.reduction.core.designsystem.widgetBackdrop
import com.pace.reduction.data.datastore.pacePreferencesDataStore
import com.pace.reduction.data.datastore.widgetSnapshotDataStore
import com.pace.reduction.domain.WidgetStatFit
import com.pace.reduction.domain.model.AccentPalette
import com.pace.reduction.domain.model.WidgetBackground
import com.pace.reduction.domain.model.WidgetSettings
import com.pace.reduction.domain.model.WidgetTick
import com.pace.reduction.feature.formatStepsCompact
import com.pace.reduction.proto.AccentPaletteProto
import com.pace.reduction.proto.PacePreferences
import com.pace.reduction.proto.WidgetBackgroundProto
import com.pace.reduction.proto.WidgetSnapshot
import com.pace.reduction.proto.WidgetStateProto
import com.pace.reduction.proto.WidgetTickProto
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.first

private val Ink = ColorProvider(Color(0xFF1B2E22), Color(0xFF0F1F16))
private val Surface = ColorProvider(Color(0xFFFFFDF7), Color(0xFFE7E2D8))
private val OnDark = ColorProvider(Color.White, Color(0xFFF2F6F3))
private val Muted = ColorProvider(Color(0xFFD3E3D8), Color(0xFFA9C2B1))
private val TrackDim = ColorProvider(Color(0x33FFFFFF), Color(0x28FFFFFF))
private val Pill = ColorProvider(Color(0x2BFFFFFF), Color(0x24FFFFFF))
private val OverCeiling = ColorProvider(Color(0xFFF0BFA6), Color(0xFFD79E7F))

/** Everything the widget needs that is not in the snapshot: how it should look, and in what accent. */
private data class WidgetStyle(
    val accent: AccentPalette,
    val settings: WidgetSettings,
)

class PaceWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(
            DpSize(110.dp, 80.dp),
            DpSize(180.dp, 100.dp),
            DpSize(300.dp, 100.dp),
        ),
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val initialSnapshot = context.widgetSnapshotDataStore.data.first()
        val initialPreferences = context.pacePreferencesDataStore.data.first()
        provideContent {
            val snapshot by context.widgetSnapshotDataStore.data.collectAsState(initialSnapshot)
            // Appearance lives in the same store the app writes its settings to, so a change in
            // Settings reaches the home screen without a second copy to keep in step.
            val preferences by context.pacePreferencesDataStore.data.collectAsState(initialPreferences)
            GlanceTheme {
                PaceWidgetContent(context, snapshot, preferences.toStyle(), LocalSize.current)
            }
        }
    }
}

class PaceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PaceWidget()
}

@Composable
private fun PaceWidgetContent(
    context: Context,
    snapshot: WidgetSnapshot,
    style: WidgetStyle,
    size: DpSize,
) {
    val configured = snapshot.localDate.isNotBlank()
    val compact = size.width < 170.dp || size.height < 95.dp
    val settings = style.settings
    val canUndo = snapshot.undoLogId.isNotBlank() && snapshot.undoExpiryEpochMs > System.currentTimeMillis()
    val coachIntent = Intent(context, MainActivity::class.java)
        .putExtra(MainActivity.EXTRA_DESTINATION, MainActivity.DESTINATION_CALL)

    Backdrop(style, size) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .clickable(actionStartActivity(Intent(context, MainActivity::class.java)))
                .padding(horizontal = HORIZONTAL_PADDING, vertical = 12.dp),
        ) {
            if (!configured) {
                SetupState(context)
                return@Column
            }

            if (snapshot.state == WidgetStateProto.WIDGET_STATE_REST) {
                RestState(context, compact)
                return@Column
            }

            // With ticking off there is nothing to keep the figure honest, so it is left out
            // rather than frozen at whatever it read hours ago.
            val showCountdown = settings.showCountdown && settings.tick != WidgetTick.OFF
            val countdownTarget = countdownTargetEpochMs(snapshot).takeIf { showCountdown }

            Row(verticalAlignment = Alignment.CenterVertically, modifier = GlanceModifier.fillMaxWidth()) {
                Column(modifier = GlanceModifier.defaultWeight()) {
                    if (!compact) {
                        Text(
                            text = context.getString(R.string.widget_brand),
                            style = TextStyle(color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold),
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = snapshot.countToday.toString(),
                            style = TextStyle(
                                color = OnDark,
                                fontSize = if (compact) 26.sp else 34.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                        )
                        Spacer(GlanceModifier.width(4.dp))
                        Text(
                            text = "/ ${snapshot.ceiling}",
                            style = TextStyle(
                                color = Muted,
                                fontSize = if (compact) 13.sp else 16.sp,
                                fontWeight = FontWeight.Medium,
                            ),
                        )
                    }
                    Text(
                        text = widgetStatus(context, snapshot),
                        maxLines = 1,
                        style = TextStyle(color = OnDark, fontSize = if (compact) 11.sp else 13.sp),
                    )
                }
                // The figure people actually want. Recalculated on the refresh cadence rather than
                // ticked: it is stated in whole minutes, so a second-by-second clock would spend
                // fifty-nine redraws out of sixty rewriting the same text.
                if (countdownTarget != null) {
                    Column(
                        modifier = GlanceModifier
                            .background(Pill)
                            .cornerRadius(18.dp)
                            .padding(horizontal = if (compact) 8.dp else 12.dp, vertical = 5.dp),
                        horizontalAlignment = Alignment.Horizontal.End,
                    ) {
                        Text(
                            text = remainingText(context, countdownTarget),
                            maxLines = 1,
                            style = TextStyle(
                                color = OnDark,
                                fontSize = if (compact) 15.sp else 20.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                        )
                        Text(
                            text = context.getString(R.string.widget_until_next_label),
                            style = TextStyle(color = Muted, fontSize = 10.sp),
                        )
                    }
                } else if (snapshot.badgeCount > 0) {
                    BadgeChip(context, snapshot)
                }
            }

            Spacer(GlanceModifier.height(if (compact) 6.dp else 9.dp))
            CeilingBar(count = snapshot.countToday, ceiling = snapshot.ceiling, size = size)

            // The single genuinely animated element: a system-driven sweep that keeps moving with
            // no process running, so a widget mid-wait never looks frozen. Tied to the wait itself
            // rather than to the countdown, because it costs nothing to refresh and stays true
            // even when the user has turned recalculation off.
            val waiting = snapshot.state in COUNTDOWN_STATES &&
                snapshot.stateUntilEpochMs > System.currentTimeMillis()
            if (settings.livePulse && waiting) {
                Spacer(GlanceModifier.height(3.dp))
                LivePulse()
            }

            if (!compact && settings.showStats) {
                Spacer(GlanceModifier.height(9.dp))
                val pills = WidgetStatFit.fit(
                    labels = statLabels(context, snapshot, settings),
                    availableDp = (size.width - HORIZONTAL_PADDING * 2).value.toDouble(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    pills.forEachIndexed { index, label ->
                        if (index > 0) Spacer(GlanceModifier.width(6.dp))
                        StatPill(text = label)
                    }
                }
            }

            // Fills the gap between the stats and the actions when the widget is tall enough to
            // render it whole; clipping a quote mid-word looks worse than omitting it.
            if (!compact && settings.showQuote && size.height >= 110.dp) {
                Spacer(GlanceModifier.height(9.dp))
                Text(
                    text = snapshot.quote.ifBlank { context.getString(R.string.widget_message_default) },
                    maxLines = 3,
                    style = TextStyle(color = Muted, fontSize = 11.sp),
                )
            }

            if (settings.showActions) {
                Spacer(GlanceModifier.defaultWeight())
                val armed = snapshot.logArmedUntilEpochMs > System.currentTimeMillis()
                Row(modifier = GlanceModifier.fillMaxWidth()) {
                    // Talking is the encouraged action, so it gets the wide primary slot; logging sits
                    // narrower alongside and needs a second tap to commit.
                    WidgetAction(
                        iconRes = R.drawable.ic_widget_chat,
                        label = context.getString(R.string.widget_talk),
                        contentDescription = context.getString(R.string.widget_talk),
                        modifier = GlanceModifier.defaultWeight(),
                        onClick = actionStartActivity(coachIntent),
                    )
                    Spacer(GlanceModifier.width(8.dp))
                    if (canUndo) {
                        WidgetAction(
                            iconRes = R.drawable.ic_widget_undo,
                            label = context.getString(R.string.undo),
                            contentDescription = context.getString(R.string.undo),
                            modifier = GlanceModifier.defaultWeight(),
                            onClick = actionRunCallback<WidgetUndoAction>(),
                            subdued = true,
                        )
                    } else {
                        WidgetAction(
                            iconRes = if (armed) R.drawable.ic_widget_confirm else R.drawable.ic_widget_log,
                            label = context.getString(
                                if (armed) R.string.widget_log_confirm else R.string.widget_log_short,
                            ),
                            contentDescription = context.getString(R.string.widget_log),
                            modifier = GlanceModifier.defaultWeight(),
                            onClick = actionRunCallback<WidgetLogAction>(),
                            subdued = !armed,
                            emphasised = armed,
                        )
                    }
                }
            }
        }
    }
}

/**
 * The widget's card: an accent fill, an optional sheen, and an optional highlight behind the count.
 *
 * The gradient is an overlay drawable rather than a per-accent resource, so the five palettes and
 * the three background styles are fifteen combinations built from two files.
 */
@Composable
private fun Backdrop(style: WidgetStyle, size: DpSize, content: @Composable () -> Unit) {
    val settings = style.settings
    val fill = style.accent.widgetBackdrop(
        opacityPercent = settings.opacityPercent,
        glass = settings.background == WidgetBackground.GLASS,
    )
    val radius = settings.cornerRadiusDp.dp
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(fill.day, fill.night))
            .cornerRadius(radius),
    ) {
        if (settings.background != WidgetBackground.SOLID) {
            Image(
                provider = ImageProvider(R.drawable.widget_scrim),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = GlanceModifier.fillMaxSize().cornerRadius(radius),
            )
        }
        // The highlight behind the count needs room to fall off; on a two-cell widget it would
        // just wash the whole card out.
        if (settings.background == WidgetBackground.GRADIENT && size.width >= 170.dp) {
            Image(
                provider = ImageProvider(R.drawable.widget_glow),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = GlanceModifier.fillMaxSize().cornerRadius(radius),
            )
        }
        content()
    }
}

@Composable
private fun SetupState(context: Context) {
    Column(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.Vertical.CenterVertically) {
        Text(
            text = context.getString(R.string.widget_setup_title),
            style = TextStyle(color = OnDark, fontSize = 17.sp, fontWeight = FontWeight.Bold),
        )
        Text(
            text = context.getString(R.string.widget_setup_body),
            style = TextStyle(color = Muted, fontSize = 12.sp),
        )
    }
}

/**
 * Ceiling meter: a lit bar for what's logged over a dim track for the room left.
 *
 * Widths come from the real widget size rather than layout weights, because Glance only offers
 * equal weights, which cannot express "5 of 10".
 */
@Composable
private fun CeilingBar(count: Int, ceiling: Int, size: DpSize) {
    val fraction = if (ceiling > 0) (count.toFloat() / ceiling).coerceIn(0f, 1f) else 0f
    val trackWidth = (size.width - HORIZONTAL_PADDING * 2).coerceAtLeast(40.dp)
    val litWidth = trackWidth * fraction
    val over = count > ceiling

    Box(
        modifier = GlanceModifier
            .width(trackWidth)
            .height(6.dp)
            .cornerRadius(3.dp)
            .background(TrackDim),
    ) {
        if (fraction > 0f) {
            Box(
                modifier = GlanceModifier
                    .width(litWidth)
                    .height(6.dp)
                    .cornerRadius(3.dp)
                    .background(if (over) OverCeiling else Surface),
            ) {}
        }
    }
}

/**
 * The stat pills, most worth showing first. How many of them actually appear is decided by
 * [WidgetStatFit] from the widget's width, so a two-cell widget drops the tail rather than clipping
 * it mid-word.
 *
 * Walking sits ahead of the money because step counting is off until the user switches it on:
 * having done so is a statement that they want the walking counted, whereas the money saved is
 * already repeated across the Today and Progress screens.
 */
private fun statLabels(
    context: Context,
    snapshot: WidgetSnapshot,
    settings: WidgetSettings,
): List<String> = buildList {
    add(context.getString(R.string.widget_free_for, shortDuration(snapshot.smokeFreeMinutes)))
    if (settings.showSteps && snapshot.stepsToday > 0) {
        val locale = context.resources.configuration.locales[0]
        add(context.getString(R.string.widget_steps, formatStepsCompact(snapshot.stepsToday, locale)))
    }
    if (snapshot.moneySaved >= 1.0) {
        add(
            context.getString(
                R.string.widget_saved,
                "${snapshot.moneySaved.toInt()} ${snapshot.currencyCode}",
            ),
        )
    } else {
        add(context.getString(R.string.widget_avoided, snapshot.cigarettesAvoided))
    }
    // The badge count lives in the top-right corner; repeating it here was noise.
    if (settings.showStreak && snapshot.zeroDayStreak > 0) {
        add(context.getString(R.string.widget_streak, snapshot.zeroDayStreak))
    }
}

@Composable
private fun StatPill(text: String) {
    Text(
        text = text,
        maxLines = 1,
        modifier = GlanceModifier
            .background(Pill)
            .cornerRadius(11.dp)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        style = TextStyle(color = OnDark, fontSize = 11.sp, fontWeight = FontWeight.Medium),
    )
}

/**
 * The indeterminate sweep. See `widget_pulse.xml` for why this is the only thing that truly moves.
 *
 * The size has to be stated on the Glance side. Glance wraps embedded RemoteViews in a container
 * sized from its modifiers, and a `match_parent` child of an unsized container claims the column's
 * whole remaining height — which silently swallows everything below it.
 */
@Composable
private fun LivePulse() {
    val context = LocalContext.current
    AndroidRemoteViews(
        remoteViews = RemoteViews(context.packageName, R.layout.widget_pulse),
        modifier = GlanceModifier.fillMaxWidth().height(3.dp),
    )
}

/**
 * The moment the current wait ends, or null when a countdown would not earn its space.
 *
 * Past the cap the widget stops counting and leans on the status line's "until 07:30", which is
 * exact and never goes stale. That also keeps the refresh chain off an eight-hour rest window,
 * where a widget nobody is looking at would otherwise wake the app all night.
 */
private fun countdownTargetEpochMs(snapshot: WidgetSnapshot): Long? {
    if (snapshot.state !in COUNTDOWN_STATES) return null
    val remaining = snapshot.stateUntilEpochMs - System.currentTimeMillis()
    return snapshot.stateUntilEpochMs.takeIf { remaining in 1..WIDGET_COUNTDOWN_MAX_MS }
}

internal val COUNTDOWN_STATES = setOf(
    WidgetStateProto.WIDGET_STATE_SPACING,
    WidgetStateProto.WIDGET_STATE_MORNING_HOLD,
)

/** Above this, show the absolute time instead of a countdown. */
internal const val WIDGET_COUNTDOWN_MAX_MS = 4 * 60 * 60 * 1_000L

/**
 * Whole minutes, rounded up so the widget never reads "0m" while there is still time on the clock.
 *
 * Minutes rather than seconds is what lets this be plain text refreshed on a cadence instead of a
 * system Chronometer — the framework's clock has no format without seconds in it.
 */
private fun remainingText(context: Context, targetEpochMs: Long): String {
    val minutes = ((targetEpochMs - System.currentTimeMillis() + 59_999) / 60_000L).coerceAtLeast(0)
    return if (minutes >= 60) {
        context.getString(R.string.widget_remaining_hours, minutes / 60, minutes % 60)
    } else {
        context.getString(R.string.widget_remaining_minutes, minutes)
    }
}

private fun shortDuration(minutes: Int): String {
    val safe = minutes.coerceAtLeast(0)
    return when {
        safe >= 24 * 60 -> "${safe / (24 * 60)}d"
        safe >= 60 -> "${safe / 60}h"
        else -> "${safe}m"
    }
}

/** A deliberately neutral overnight face: no count, time, stats, quote, or action prompt. */
@Composable
private fun RestState(context: Context, compact: Boolean) {
    Column(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = context.getString(R.string.rest_mode_title),
            style = TextStyle(
                color = OnDark,
                fontSize = if (compact) 18.sp else 22.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
        Spacer(GlanceModifier.height(5.dp))
        Text(
            text = context.getString(R.string.widget_rest_body),
            maxLines = 2,
            style = TextStyle(color = Muted, fontSize = if (compact) 11.sp else 13.sp),
        )
    }
}

@Composable
private fun BadgeChip(context: Context, snapshot: WidgetSnapshot) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = GlanceModifier
            .background(Pill)
            .cornerRadius(14.dp)
            .padding(horizontal = 9.dp, vertical = 6.dp),
    ) {
        Image(
            provider = ImageProvider(R.drawable.ic_badge_widget),
            contentDescription = null,
            modifier = GlanceModifier.size(14.dp),
        )
        Spacer(GlanceModifier.width(5.dp))
        Text(
            text = snapshot.badgeCount.toString(),
            style = TextStyle(color = OnDark, fontSize = 12.sp, fontWeight = FontWeight.Bold),
        )
    }
}

@Composable
private fun WidgetAction(
    iconRes: Int,
    label: String,
    contentDescription: String,
    modifier: GlanceModifier,
    onClick: androidx.glance.action.Action,
    subdued: Boolean = false,
    emphasised: Boolean = false,
) {
    val background = when {
        emphasised -> ColorProvider(Color(0xFFE9B44C), Color(0xFFD79E33))
        subdued -> Pill
        else -> Surface
    }
    val foreground = if (subdued) OnDark else Ink
    Row(
        modifier = modifier
            .background(background)
            .cornerRadius(18.dp)
            .clickable(onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
    ) {
        Image(
            provider = ImageProvider(iconRes),
            contentDescription = contentDescription,
            colorFilter = ColorFilter.tint(foreground),
            modifier = GlanceModifier.size(17.dp),
        )
        Spacer(GlanceModifier.width(6.dp))
        Text(
            text = label,
            maxLines = 1,
            style = TextStyle(color = foreground, fontSize = 13.sp, fontWeight = FontWeight.Bold),
        )
    }
}

private val HORIZONTAL_PADDING = 14.dp

private fun widgetStatus(context: Context, snapshot: WidgetSnapshot): String = when (snapshot.state) {
    WidgetStateProto.WIDGET_STATE_SPACING -> context.getString(
        R.string.widget_spacing_until,
        snapshot.stateUntilEpochMs.asLocalTime(),
    )
    WidgetStateProto.WIDGET_STATE_WINDOW_MET -> context.getString(R.string.widget_window_met)
    WidgetStateProto.WIDGET_STATE_MORNING_HOLD -> context.getString(
        R.string.widget_hold_until,
        snapshot.stateUntilEpochMs.asLocalTime(),
    )
    WidgetStateProto.WIDGET_STATE_REST -> context.getString(
        R.string.widget_rest_until,
        snapshot.stateUntilEpochMs.asLocalTime(),
    )
    WidgetStateProto.WIDGET_STATE_CEILING -> context.getString(R.string.widget_ceiling)
    WidgetStateProto.WIDGET_STATE_RECOVERY -> context.getString(R.string.widget_recovery)
    else -> snapshot.safeMessage.ifBlank { context.getString(R.string.widget_message_default) }
}

private fun Long.asLocalTime(): String = Instant.ofEpochMilli(this)
    .atZone(ZoneId.systemDefault())
    .format(DateTimeFormatter.ofPattern("HH:mm"))

/**
 * Reads appearance straight from the preferences proto.
 *
 * The widget deliberately does not go through the repository: it is rendered from a broadcast
 * receiver, where constructing the database and its migrations to find out which green to use
 * would be an absurd amount of work for a colour.
 */
private fun PacePreferences.toStyle(): WidgetStyle = WidgetStyle(
    accent = when (accentPalette) {
        AccentPaletteProto.ACCENT_PALETTE_OCEAN -> AccentPalette.OCEAN
        AccentPaletteProto.ACCENT_PALETTE_EMBER -> AccentPalette.EMBER
        AccentPaletteProto.ACCENT_PALETTE_VIOLET -> AccentPalette.VIOLET
        AccentPaletteProto.ACCENT_PALETTE_SLATE -> AccentPalette.SLATE
        else -> AccentPalette.SAGE
    },
    settings = WidgetSettings(
        background = when (widgetBackground) {
            WidgetBackgroundProto.WIDGET_BACKGROUND_SOLID -> WidgetBackground.SOLID
            WidgetBackgroundProto.WIDGET_BACKGROUND_GLASS -> WidgetBackground.GLASS
            else -> WidgetBackground.GRADIENT
        },
        cornerRadiusDp = widgetCornerRadiusDp.takeIf { it in 1..40 } ?: 24,
        opacityPercent = widgetOpacityPercent.takeIf { it in 35..100 } ?: 100,
        showQuote = !widgetHideQuote,
        showStats = !widgetHideStats,
        showActions = !widgetHideActions,
        showCountdown = !widgetHideCountdown,
        showStreak = !widgetHideStreak,
        showSteps = !widgetHideSteps,
        confirmLog = !widgetSkipLogConfirm,
        livePulse = !widgetDisablePulse,
        tick = when (widgetTick) {
            WidgetTickProto.WIDGET_TICK_LIVE -> WidgetTick.LIVE
            WidgetTickProto.WIDGET_TICK_OFF -> WidgetTick.OFF
            else -> WidgetTick.SAVER
        },
    ),
)

/**
 * Two-step by design. A widget sits under a thumb all day, and an accidental tap writes a
 * cigarette that never happened — which corrupts exactly the history the whole app reasons from.
 * The first tap only arms the button, unless the user has turned confirmation off.
 */
class WidgetLogAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: androidx.glance.action.ActionParameters,
    ) {
        if (context.repository().armOrCommitWidgetLog()) {
            context.repository().logCigarette(source = "WIDGET")
            WidgetUndoExpiryWorker.schedule(context)
        } else {
            WidgetDisarmWorker.schedule(context)
        }
    }
}

class WidgetUndoAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: androidx.glance.action.ActionParameters,
    ) {
        val snapshot = context.widgetSnapshotDataStore.data.first()
        if (snapshot.undoLogId.isNotBlank() && snapshot.undoExpiryEpochMs >= System.currentTimeMillis()) {
            context.repository().undoLog(snapshot.undoLogId)
        } else {
            context.repository().refreshWidgetSnapshot()
        }
    }
}

private fun Context.repository() =
    (applicationContext as PaceApplication).container.repository
