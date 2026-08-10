package com.pace.reduction.core.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.pace.reduction.MainActivity
import com.pace.reduction.PaceApplication
import com.pace.reduction.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object PaceNotifications {
    const val CHECKINS = "pace_checkins"
    const val ACHIEVEMENTS = "pace_achievements"
    const val LOCATION_CUES = "pace_location_cues"
    const val TIMER = "pace_timer"
    private const val COACHING_ID = 2001
    private const val LOCATION_ID = 2002
    private const val NUDGE_ID = 2003
    private const val CHECKUP_ID = 2004
    private const val SUPPORT_GROUP = "pace_support"

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(CHECKINS, context.getString(R.string.channel_checkins), NotificationManager.IMPORTANCE_DEFAULT),
                NotificationChannel(ACHIEVEMENTS, context.getString(R.string.channel_achievements), NotificationManager.IMPORTANCE_LOW),
                NotificationChannel(LOCATION_CUES, context.getString(R.string.channel_location), NotificationManager.IMPORTANCE_DEFAULT).apply {
                    lockscreenVisibility = NotificationCompat.VISIBILITY_PRIVATE
                },
                NotificationChannel(TIMER, context.getString(R.string.channel_timer), NotificationManager.IMPORTANCE_DEFAULT),
            ),
        )
    }

    fun canPost(context: Context): Boolean =
        (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED) && NotificationManagerCompat.from(context).areNotificationsEnabled()

    @SuppressLint("MissingPermission")
    fun postCoaching(context: Context, privateOnLockScreen: Boolean = true) {
        if (!canPost(context)) return
        val toolkitIntent = Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_DESTINATION, MainActivity.DESTINATION_TOOLKIT)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val toolkitPending = PendingIntent.getActivity(
            context,
            11,
            toolkitIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val dismissPending = PendingIntent.getBroadcast(
            context,
            12,
            Intent(context, NotificationDismissReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHECKINS)
            .setSmallIcon(R.drawable.ic_pace_notification)
            .setContentTitle(context.getString(R.string.notification_checkin_title))
            .setContentText(context.getString(R.string.notification_checkin_body))
            .setContentIntent(toolkitPending)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setGroup(SUPPORT_GROUP)
            .setOnlyAlertOnce(true)
            .addAction(0, context.getString(R.string.pause_five), toolkitPending)
            .addAction(0, context.getString(R.string.dismiss), dismissPending)
            .applyLockScreenPrivacy(context, privateOnLockScreen)
            .build()
        NotificationManagerCompat.from(context).notify(COACHING_ID, notification)
    }

    /**
     * AI-written nudge that lands shortly before the next planned window and opens the coach chat
     * so the conversation can continue where the notification left off.
     */
    @SuppressLint("MissingPermission")
    fun postCoachNudge(context: Context, message: String, privateOnLockScreen: Boolean = true) {
        if (!canPost(context)) return
        val text = message.ifBlank { context.getString(R.string.notification_nudge_fallback) }
        val coachIntent = Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_DESTINATION, MainActivity.DESTINATION_COACH)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val coachPending = PendingIntent.getActivity(
            context,
            14,
            coachIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val dismissPending = PendingIntent.getBroadcast(
            context,
            15,
            Intent(context, NotificationDismissReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        NotificationManagerCompat.from(context).notify(
            NUDGE_ID,
            NotificationCompat.Builder(context, CHECKINS)
                .setSmallIcon(R.drawable.ic_pace_notification)
                .setContentTitle(context.getString(R.string.notification_nudge_title))
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(coachPending)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setGroup(SUPPORT_GROUP)
                .setOnlyAlertOnce(true)
                .addAction(0, context.getString(R.string.notification_nudge_action), coachPending)
                .addAction(0, context.getString(R.string.dismiss), dismissPending)
                .applyLockScreenPrivacy(context, privateOnLockScreen)
                .build(),
        )
    }

    /** Unprompted check-in written by the coach; opens the chat so the user can answer back. */
    @SuppressLint("MissingPermission")
    fun postCoachCheckup(context: Context, message: String, privateOnLockScreen: Boolean = true) {
        if (!canPost(context) || message.isBlank()) return
        val coachPending = PendingIntent.getActivity(
            context,
            16,
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_DESTINATION, MainActivity.DESTINATION_COACH)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val dismissPending = PendingIntent.getBroadcast(
            context,
            17,
            Intent(context, NotificationDismissReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        NotificationManagerCompat.from(context).notify(
            CHECKUP_ID,
            NotificationCompat.Builder(context, CHECKINS)
                .setSmallIcon(R.drawable.ic_pace_notification)
                .setContentTitle(context.getString(R.string.notification_checkup_title))
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setContentIntent(coachPending)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setGroup(SUPPORT_GROUP)
                .setOnlyAlertOnce(true)
                .addAction(0, context.getString(R.string.notification_nudge_action), coachPending)
                .addAction(0, context.getString(R.string.dismiss), dismissPending)
                .applyLockScreenPrivacy(context, privateOnLockScreen)
                .build(),
        )
    }

    @SuppressLint("MissingPermission")
    fun postLocationCue(context: Context) {
        if (!canPost(context)) return
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_DESTINATION, MainActivity.DESTINATION_TOOLKIT)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending = PendingIntent.getActivity(
            context,
            13,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        NotificationManagerCompat.from(context).notify(
            LOCATION_ID,
            NotificationCompat.Builder(context, LOCATION_CUES)
                .setSmallIcon(R.drawable.ic_pace_notification)
                .setContentTitle(context.getString(R.string.notification_location_title))
                .setContentText(context.getString(R.string.notification_location_body))
                .setContentIntent(pending)
                .setAutoCancel(true)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .build(),
        )
    }

    /** Clears any daytime prompt that might otherwise remain visible after quiet hours begin. */
    fun cancelReminders(context: Context) {
        NotificationManagerCompat.from(context).run {
            cancel(COACHING_ID)
            cancel(NUDGE_ID)
            cancel(CHECKUP_ID)
            cancel(LOCATION_ID)
        }
    }

    private fun NotificationCompat.Builder.applyLockScreenPrivacy(
        context: Context,
        privateOnLockScreen: Boolean,
    ): NotificationCompat.Builder = apply {
        if (privateOnLockScreen) {
            setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            setPublicVersion(
                NotificationCompat.Builder(context, CHECKINS)
                    .setSmallIcon(R.drawable.ic_pace_notification)
                    .setContentTitle(context.getString(R.string.app_name))
                    .setContentText(context.getString(R.string.notification_private_preview))
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .build(),
            )
        } else {
            setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        }
    }
}

class NotificationDismissReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                (context.applicationContext as PaceApplication).container.repository.dismissCoachingNotifications()
                PaceNotifications.cancelReminders(context)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
