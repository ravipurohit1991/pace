package com.pace.reduction.widget

import android.content.Context
import android.content.Intent
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
import com.pace.reduction.data.datastore.widgetSnapshotDataStore
import com.pace.reduction.proto.WidgetSnapshot
import com.pace.reduction.proto.WidgetStateProto
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.first

private val Ink = ColorProvider(Color(0xFF23402D), Color(0xFF0F1F16))
private val Surface = ColorProvider(Color(0xFFFFFDF7), Color(0xFFE7E2D8))
private val OnDark = ColorProvider(Color.White, Color(0xFFF2F6F3))
private val Muted = ColorProvider(Color(0xFFC5DBCB), Color(0xFF9FBCA8))
private val TrackDim = ColorProvider(Color(0x33FFFFFF), Color(0x28FFFFFF))

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
        provideContent {
            val snapshot by context.widgetSnapshotDataStore.data.collectAsState(initialSnapshot)
            GlanceTheme { PaceWidgetContent(context, snapshot, LocalSize.current) }
        }
    }
}

class PaceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PaceWidget()
}

@Composable
private fun PaceWidgetContent(context: Context, snapshot: WidgetSnapshot, size: DpSize) {
    val configured = snapshot.localDate.isNotBlank()
    val compact = size.width < 170.dp || size.height < 95.dp
    val wide = size.width >= 280.dp
    val canUndo = snapshot.undoLogId.isNotBlank() && snapshot.undoExpiryEpochMs > System.currentTimeMillis()
    val coachIntent = Intent(context, MainActivity::class.java)
        .putExtra(MainActivity.EXTRA_DESTINATION, MainActivity.DESTINATION_COACH)

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(Color(0xFF3F6A4E), Color(0xFF1E3A29)))
            .cornerRadius(24.dp)
            .clickable(actionStartActivity(Intent(context, MainActivity::class.java)))
            .padding(horizontal = HORIZONTAL_PADDING, vertical = 12.dp),
    ) {
        if (!configured) {
            SetupState(context)
            return@Column
        }

        val countdown = countdownLabel(snapshot)

        Row(verticalAlignment = Alignment.CenterVertically, modifier = GlanceModifier.fillMaxWidth()) {
            Column(modifier = GlanceModifier.defaultWeight()) {
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
            // The headline number people actually want: how long until the next one is due.
            if (countdown != null) {
                Column(horizontalAlignment = Alignment.Horizontal.End) {
                    Text(
                        text = countdown,
                        style = TextStyle(
                            color = OnDark,
                            fontSize = if (compact) 16.sp else 22.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                    Text(
                        text = context.getString(R.string.widget_until_next_label),
                        style = TextStyle(color = Muted, fontSize = 10.sp),
                    )
                }
            } else if (!compact && snapshot.latestBadgeId.isNotBlank()) {
                BadgeChip(context, snapshot)
            }
        }

        Spacer(GlanceModifier.height(if (compact) 6.dp else 9.dp))
        CeilingBar(count = snapshot.countToday, ceiling = snapshot.ceiling, size = size)

        if (!compact) {
            Spacer(GlanceModifier.height(9.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatPill(text = context.getString(R.string.widget_free_for, shortDuration(snapshot.smokeFreeMinutes)))
                Spacer(GlanceModifier.width(6.dp))
                if (snapshot.moneySaved >= 1.0) {
                    StatPill(
                        text = context.getString(
                            R.string.widget_saved,
                            "${snapshot.moneySaved.toInt()} ${snapshot.currencyCode}",
                        ),
                    )
                } else {
                    StatPill(text = context.getString(R.string.widget_avoided, snapshot.cigarettesAvoided))
                }
                if (wide && snapshot.zeroDayStreak > 0) {
                    Spacer(GlanceModifier.width(6.dp))
                    StatPill(text = context.getString(R.string.widget_streak, snapshot.zeroDayStreak))
                }
                if (wide && snapshot.badgeCount > 0) {
                    Spacer(GlanceModifier.width(6.dp))
                    BadgeChip(context, snapshot)
                }
            }
        }

        // Only when there is genuine vertical room, otherwise the line gets clipped mid-word.
        if (!compact && size.height >= 140.dp && snapshot.quote.isNotBlank()) {
            Spacer(GlanceModifier.height(9.dp))
            Text(
                text = snapshot.quote,
                maxLines = 3,
                style = TextStyle(color = Muted, fontSize = 11.sp),
            )
        }

        Spacer(GlanceModifier.defaultWeight())
        // Icons rather than labels: the widget is glanceable, and these two actions are obvious.
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            WidgetAction(
                iconRes = R.drawable.ic_widget_log,
                contentDescription = context.getString(R.string.widget_log),
                modifier = GlanceModifier.defaultWeight(),
                onClick = actionRunCallback<WidgetLogAction>(),
            )
            Spacer(GlanceModifier.width(8.dp))
            if (canUndo) {
                WidgetAction(
                    iconRes = R.drawable.ic_widget_undo,
                    contentDescription = context.getString(R.string.undo),
                    modifier = GlanceModifier.defaultWeight(),
                    onClick = actionRunCallback<WidgetUndoAction>(),
                    subdued = true,
                )
            } else {
                WidgetAction(
                    iconRes = R.drawable.ic_widget_chat,
                    contentDescription = context.getString(R.string.have_craving),
                    modifier = GlanceModifier.defaultWeight(),
                    onClick = actionStartActivity(coachIntent),
                    subdued = true,
                )
            }
        }
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
                    .background(
                        if (over) ColorProvider(Color(0xFFE5A3A3), Color(0xFFC98686)) else Surface,
                    ),
            ) {}
        }
    }
}

@Composable
private fun StatPill(text: String) {
    Text(
        text = text,
        maxLines = 1,
        modifier = GlanceModifier
            .background(ColorProvider(Color(0x2BFFFFFF), Color(0x24FFFFFF)))
            .cornerRadius(11.dp)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        style = TextStyle(color = OnDark, fontSize = 11.sp, fontWeight = FontWeight.Medium),
    )
}

/**
 * Time left until the next planned window, or null when nothing is being waited for.
 * Computed at render time; [WidgetBoundaryWorker] re-renders when the window arrives.
 */
private fun countdownLabel(snapshot: WidgetSnapshot): String? {
    if (snapshot.state != WidgetStateProto.WIDGET_STATE_SPACING &&
        snapshot.state != WidgetStateProto.WIDGET_STATE_MORNING_HOLD &&
        snapshot.state != WidgetStateProto.WIDGET_STATE_REST
    ) {
        return null
    }
    val remaining = snapshot.stateUntilEpochMs - System.currentTimeMillis()
    if (remaining <= 0) return null
    val minutes = remaining / 60_000
    return when {
        minutes >= 60 -> "${minutes / 60}h ${minutes % 60}m"
        else -> "${minutes}m"
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

@Composable
private fun BadgeChip(context: Context, snapshot: WidgetSnapshot) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = GlanceModifier
            .background(ColorProvider(Color(0x2BFFFFFF), Color(0x24FFFFFF)))
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
    contentDescription: String,
    modifier: GlanceModifier,
    onClick: androidx.glance.action.Action,
    subdued: Boolean = false,
) {
    Box(
        modifier = modifier
            .background(if (subdued) ColorProvider(Color(0x2BFFFFFF), Color(0x24FFFFFF)) else Surface)
            .cornerRadius(18.dp)
            .clickable(onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            provider = ImageProvider(iconRes),
            contentDescription = contentDescription,
            colorFilter = ColorFilter.tint(if (subdued) OnDark else Ink),
            modifier = GlanceModifier.size(20.dp),
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

class WidgetLogAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: androidx.glance.action.ActionParameters,
    ) {
        context.repository().logCigarette(source = "WIDGET")
        WidgetUndoExpiryWorker.schedule(context)
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
