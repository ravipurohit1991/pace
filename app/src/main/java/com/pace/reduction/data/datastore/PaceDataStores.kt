package com.pace.reduction.data.datastore

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import com.google.protobuf.InvalidProtocolBufferException
import com.pace.reduction.proto.CoachingToneProto
import com.pace.reduction.proto.PacePreferences
import com.pace.reduction.proto.ReminderIntensityProto
import com.pace.reduction.proto.WidgetSnapshot
import java.io.InputStream
import java.io.OutputStream

object PacePreferencesSerializer : Serializer<PacePreferences> {
    override val defaultValue: PacePreferences = PacePreferences.newBuilder()
        .setSchemaVersion(1)
        .setBaselinePerDay(12)
        .setDailyCeiling(12)
        .setMinimumGapMinutes(90)
        .setWakeMinutes(7 * 60)
        .setSleepMinutes(22 * 60 + 30)
        .setWeekendWakeMinutes(8 * 60)
        .setMorningHoldMinutes(30)
        .setReductionStep(1)
        .setReviewIntervalDays(7)
        .setCigarettesPerPack(20)
        .setCurrencyCode("DKK")
        .setCoachingTone(CoachingToneProto.COACHING_TONE_SUPPORTIVE)
        .setReminderIntensity(ReminderIntensityProto.REMINDER_INTENSITY_OFF)
        .setNotificationPrivate(true)
        .setHapticsEnabled(true)
        .build()

    override suspend fun readFrom(input: InputStream): PacePreferences = try {
        PacePreferences.parseFrom(input)
    } catch (error: InvalidProtocolBufferException) {
        throw CorruptionException("Cannot read Pace preferences", error)
    }

    override suspend fun writeTo(t: PacePreferences, output: OutputStream) = t.writeTo(output)
}

object WidgetSnapshotSerializer : Serializer<WidgetSnapshot> {
    override val defaultValue: WidgetSnapshot = WidgetSnapshot.getDefaultInstance()

    override suspend fun readFrom(input: InputStream): WidgetSnapshot = try {
        WidgetSnapshot.parseFrom(input)
    } catch (error: InvalidProtocolBufferException) {
        throw CorruptionException("Cannot read Pace widget state", error)
    }

    override suspend fun writeTo(t: WidgetSnapshot, output: OutputStream) = t.writeTo(output)
}

val Context.pacePreferencesDataStore: DataStore<PacePreferences> by dataStore(
    fileName = "pace_preferences.pb",
    serializer = PacePreferencesSerializer,
)

val Context.widgetSnapshotDataStore: DataStore<WidgetSnapshot> by dataStore(
    fileName = "widget_snapshot.pb",
    serializer = WidgetSnapshotSerializer,
)

