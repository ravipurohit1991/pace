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
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
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
            PaceWidgetContent(context, snapshot, LocalSize.current)
        }
    }
}

class PaceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PaceWidget()
}

@Composable
private fun PaceWidgetContent(context: Context, snapshot: WidgetSnapshot, size: DpSize) {
    val configured = snapshot.localDate.isNotBlank()
    val title = if (configured) {
        context.getString(R.string.widget_count, snapshot.countToday, snapshot.ceiling)
    } else {
        context.getString(R.string.widget_setup_title)
    }
    val status = if (configured) widgetStatus(context, snapshot) else context.getString(R.string.widget_setup_body)
    val canUndo = snapshot.undoLogId.isNotBlank() && snapshot.undoExpiryEpochMs > System.currentTimeMillis()
    val compact = size.width < 170.dp || size.height < 95.dp
    val wide = size.width >= 280.dp
    val toolkitIntent = Intent(context, MainActivity::class.java)
        .putExtra(MainActivity.EXTRA_DESTINATION, MainActivity.DESTINATION_TOOLKIT)

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(Color(0xFF385B45), Color(0xFF20372A)))
            .cornerRadius(22.dp)
            .clickable(actionStartActivity(Intent(context, MainActivity::class.java)))
            .padding(16.dp),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        Text(
            text = context.getString(R.string.app_name),
            style = TextStyle(
                color = ColorProvider(Color(0xFFD9E8DC), Color(0xFFAECFB5)),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
        Spacer(GlanceModifier.height(if (compact) 2.dp else 6.dp))
        Text(
            text = title,
            style = TextStyle(
                color = ColorProvider(Color.White, Color.White),
                fontSize = if (compact) 17.sp else 22.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
        if (!compact) {
            Text(
                text = status,
                maxLines = 2,
                style = TextStyle(
                    color = ColorProvider(Color(0xFFEAF2EB), Color(0xFFD9E8DC)),
                    fontSize = 13.sp,
                ),
            )
            if (wide) {
                Text(
                    text = snapshot.safeMessage,
                    maxLines = 1,
                    style = TextStyle(
                        color = ColorProvider(Color(0xFFD9E8DC), Color(0xFFAECFB5)),
                        fontSize = 11.sp,
                    ),
                )
            }
        } else if (snapshot.lastActiveLogEpochMs > 0) {
            val gapMinutes = ((System.currentTimeMillis() - snapshot.lastActiveLogEpochMs) / 60_000).coerceAtLeast(0)
            Text(
                text = context.getString(R.string.widget_gap_minutes, gapMinutes),
                style = TextStyle(
                    color = ColorProvider(Color(0xFFEAF2EB), Color(0xFFD9E8DC)),
                    fontSize = 11.sp,
                ),
            )
        }
        Spacer(GlanceModifier.defaultWeight())
        if (configured) {
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                if (compact) {
                    WidgetAction(
                        text = context.getString(R.string.have_craving),
                        modifier = GlanceModifier.defaultWeight(),
                        onClick = actionStartActivity(toolkitIntent),
                    )
                } else {
                    WidgetAction(
                        text = context.getString(R.string.widget_log),
                        modifier = GlanceModifier.defaultWeight(),
                        onClick = actionRunCallback<WidgetLogAction>(),
                    )
                }
                if (canUndo && !compact) {
                    Spacer(GlanceModifier.width(8.dp))
                    WidgetAction(
                        text = context.getString(R.string.undo),
                        modifier = GlanceModifier.defaultWeight(),
                        onClick = actionRunCallback<WidgetUndoAction>(),
                    )
                } else if (!compact) {
                    Spacer(GlanceModifier.width(8.dp))
                    WidgetAction(
                        text = context.getString(R.string.pause_five),
                        modifier = GlanceModifier.defaultWeight(),
                        onClick = actionStartActivity(toolkitIntent),
                    )
                }
            }
        }
    }
}

@Composable
private fun WidgetAction(
    text: String,
    modifier: GlanceModifier,
    onClick: androidx.glance.action.Action,
) {
    Text(
        text = text,
        modifier = modifier
            .background(ColorProvider(Color(0xFFFFFDF7), Color(0xFFE7E2D8)))
            .cornerRadius(16.dp)
            .clickable(onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        style = TextStyle(
            color = ColorProvider(Color(0xFF23402D), Color(0xFF173824)),
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
        ),
    )
}

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
