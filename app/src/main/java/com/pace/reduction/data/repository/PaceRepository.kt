package com.pace.reduction.data.repository

import android.content.Context
import android.app.NotificationManager
import androidx.work.WorkManager
import androidx.datastore.core.DataStore
import androidx.glance.appwidget.updateAll
import androidx.room.withTransaction
import com.pace.reduction.R
import com.pace.reduction.core.notifications.NotificationPolicy
import com.pace.reduction.core.network.OllamaClient
import com.pace.reduction.core.security.SecretVault
import com.pace.reduction.data.db.CoachMessageEntity
import com.pace.reduction.data.seed.ProvisioningSeeder
import com.pace.reduction.domain.model.AiSettings
import com.pace.reduction.domain.model.CoachMessage
import com.google.protobuf.ByteString
import com.pace.reduction.data.db.CigaretteLogEntity
import com.pace.reduction.data.db.AchievementEntity
import com.pace.reduction.data.db.DailyPlanSnapshotEntity
import com.pace.reduction.data.db.ExternalBreakEntity
import com.pace.reduction.data.db.PaceDatabase
import com.pace.reduction.data.db.UrgeSessionEntity
import com.pace.reduction.data.backup.AchievementBackup
import com.pace.reduction.data.backup.ExternalBreakBackup
import com.pace.reduction.data.backup.LogBackup
import com.pace.reduction.data.backup.PaceBackup
import com.pace.reduction.data.backup.PaceBackupValidator
import com.pace.reduction.data.backup.PlanBackup
import com.pace.reduction.data.backup.SnapshotBackup
import com.pace.reduction.data.backup.UrgeBackup
import com.pace.reduction.domain.model.ActivePause
import com.pace.reduction.domain.model.Achievement
import com.pace.reduction.domain.model.CigaretteLog
import com.pace.reduction.domain.model.CoachingTone
import com.pace.reduction.domain.model.DailyPlanSnapshot
import com.pace.reduction.domain.model.PlanSettings
import com.pace.reduction.domain.model.ReminderIntensity
import com.pace.reduction.domain.model.PacingStatus
import com.pace.reduction.domain.model.ThemeMode
import com.pace.reduction.domain.model.UrgeSession
import com.pace.reduction.domain.PacingCalculator
import com.pace.reduction.domain.AdaptiveSpacing
import com.pace.reduction.domain.BadgeEngine
import com.pace.reduction.domain.CoachContext
import com.pace.reduction.domain.DailyProgress
import com.pace.reduction.domain.ProgressCalculator
import com.pace.reduction.domain.QuitProgress
import com.pace.reduction.domain.SpacingProgress
import com.pace.reduction.domain.WidgetTapGuard
import com.pace.reduction.proto.CoachingToneProto
import com.pace.reduction.proto.PacePreferences
import com.pace.reduction.proto.ReminderIntensityProto
import com.pace.reduction.proto.ThemeModeProto
import com.pace.reduction.proto.WidgetSnapshot
import com.pace.reduction.proto.WidgetStateProto
import com.pace.reduction.widget.PaceWidget
import com.pace.reduction.widget.WidgetBoundaryWorker
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

class PaceRepository(
    private val context: Context,
    private val database: PaceDatabase,
    private val preferencesStore: DataStore<PacePreferences>,
    private val widgetStore: DataStore<WidgetSnapshot>,
    private val clock: Clock = Clock.systemUTC(),
    private val zoneProvider: () -> ZoneId = { ZoneId.systemDefault() },
) {
    private val dao = database.paceDao()
    private val backupJson = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = false
    }

    val settings: Flow<PlanSettings> = preferencesStore.data.map(::toDomain)

    val activeLogs: Flow<List<CigaretteLog>> = dao.observeActiveLogs().map { entities ->
        entities.map { it.toDomain() }
    }

    val allLogs: Flow<List<CigaretteLog>> = dao.observeAllLogs().map { entities ->
        entities.map { it.toDomain() }
    }

    val urgeSessions: Flow<List<UrgeSession>> = dao.observeUrgeSessions().map { sessions ->
        sessions.map { it.toDomain() }
    }

    val dailySnapshots: Flow<List<DailyPlanSnapshot>> = dao.observeDailySnapshots().map { snapshots ->
        snapshots.map { it.toDomain() }
    }

    val achievements: Flow<List<Achievement>> = dao.observeAchievements().map { achievements ->
        achievements.map { it.toDomain() }
    }

    val aiSettings: Flow<AiSettings> = preferencesStore.data.map { preferences ->
        AiSettings(
            enabled = preferences.ollamaEnabled,
            apiKey = SecretVault.decrypt(preferences.ollamaApiKeyCiphertext.toByteArray()),
            model = preferences.ollamaModel.ifBlank { OllamaClient.DEFAULT_MODEL },
            proactiveNudges = preferences.ollamaProactiveNudges,
            systemPrompt = preferences.ollamaSystemPrompt,
            includeStats = !preferences.ollamaOmitStats,
            checkupsEnabled = preferences.ollamaCheckupsEnabled,
            checkupIntervalMinutes = preferences.ollamaCheckupIntervalMinutes.takeIf { it > 0 } ?: 180,
        )
    }

    val coachMessages: Flow<List<CoachMessage>> = dao.observeCoachMessages().map { messages ->
        messages.map { it.toDomain() }
    }

    val activePause: Flow<ActivePause> = preferencesStore.data.map { preferences ->
        ActivePause(
            sessionId = preferences.activePauseSessionId,
            startedAt = preferences.activePauseStartedEpochMs.takeIf { it > 0 }?.let(Instant::ofEpochMilli),
            endAt = preferences.activePauseEndEpochMs.takeIf { it > 0 }?.let(Instant::ofEpochMilli),
            pausedRemainingMillis = preferences.activePauseRemainingMs,
        )
    }

    suspend fun completeOnboarding(plan: PlanSettings) {
        preferencesStore.updateData { current -> current.withPlan(plan).toBuilder()
            .setOnboardingCompleted(true)
            .clearPendingDailyCeiling()
            .clearPendingCeilingEffectiveDate()
            .build() }
        ensureTodaySnapshot(plan)
        refreshWidgetSnapshot()
    }

    suspend fun updatePlan(plan: PlanSettings) {
        val now = clock.instant()
        val zone = zoneProvider()
        val date = now.atZone(zone).toLocalDate()
        val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val currentPlan = settings.first()
        val countToday = dao.activeLogsBetween(start, end).size
        preferencesStore.updateData { current ->
            if (plan.dailyCeiling < countToday) {
                current.withPlan(plan.copy(dailyCeiling = currentPlan.dailyCeiling)).toBuilder()
                    .setPendingDailyCeiling(plan.dailyCeiling)
                    .setPendingCeilingEffectiveDate(date.plusDays(1).toString())
                    .build()
            } else {
                current.withPlan(plan).toBuilder()
                    .clearPendingDailyCeiling()
                    .clearPendingCeilingEffectiveDate()
                    .build()
            }
        }
        refreshWidgetSnapshot()
    }

    suspend fun logCigarette(source: String = "APP"): String {
        val now = clock.instant()
        val id = UUID.randomUUID().toString()
        val plan = settings.first()
        val resolvedId = database.withTransaction {
            val latest = dao.latestActiveLog()
            if (source == "WIDGET" && WidgetTapGuard.shouldReuseLatest(latest?.source, latest?.recordedAtEpochMs, now.toEpochMilli())) {
                return@withTransaction requireNotNull(latest).id
            }
            ensureTodaySnapshot(plan)
            dao.insertLog(
                CigaretteLogEntity(
                    id = id,
                    occurredAtEpochMs = now.toEpochMilli(),
                    recordedAtEpochMs = now.toEpochMilli(),
                    source = source,
                    note = null,
                    reversedAtEpochMs = null,
                    reversalReason = null,
                ),
            )
            id
        }
        refreshWidgetSnapshot(undoLogId = resolvedId)
        evaluateAchievements()
        return resolvedId
    }

    /**
     * Adds a cigarette at an arbitrary past moment so history can be corrected after the fact.
     * Also backfills that day's plan snapshot, otherwise the day stays "unknown" and never counts
     * toward averages, steady days or adaptive spacing.
     */
    suspend fun addLogAt(moment: java.time.LocalDateTime): String {
        val zone = zoneProvider()
        val instant = moment.atZone(zone).toInstant()
        require(!instant.isAfter(clock.instant())) { "Cannot log a cigarette in the future" }
        val id = UUID.randomUUID().toString()
        val plan = settings.first()
        database.withTransaction {
            ensureSnapshotFor(moment.toLocalDate(), plan)
            dao.insertLog(
                CigaretteLogEntity(
                    id = id,
                    occurredAtEpochMs = instant.toEpochMilli(),
                    recordedAtEpochMs = clock.millis(),
                    source = "MANUAL",
                    note = null,
                    reversedAtEpochMs = null,
                    reversalReason = null,
                ),
            )
        }
        refreshWidgetSnapshot()
        evaluateAchievements()
        return id
    }

    /** Hard-deletes a log. Used for corrections, unlike [undoLog] which keeps a reversal record. */
    suspend fun deleteLog(id: String): Boolean {
        val removed = dao.deleteLog(id) > 0
        if (removed) {
            refreshWidgetSnapshot()
            evaluateAchievements()
        }
        return removed
    }

    /** Every log on [date], reversed ones included, so the editor can show the full picture. */
    suspend fun logsOn(date: LocalDate): List<CigaretteLog> {
        val zone = zoneProvider()
        val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return dao.allLogs()
            .filter { it.occurredAtEpochMs in start until end }
            .map { it.toDomain() }
            .sortedBy { it.occurredAt }
    }

    suspend fun undoLog(id: String): Boolean {
        val updated = dao.reverseLog(
            id = id,
            reversedAtEpochMs = clock.millis(),
            reason = "USER_UNDO",
        ) > 0
        if (updated) refreshWidgetSnapshot()
        return updated
    }

    suspend fun saveUrgeCheckIn(
        urgeBefore: Int?,
        triggerTags: Set<String>,
        note: String,
        tool: String = "PAUSE",
        completed: Boolean = false,
    ): String {
        val id = UUID.randomUUID().toString()
        dao.upsertUrgeSession(
            UrgeSessionEntity(
                id = id,
                startedAtEpochMs = clock.millis(),
                endedAtEpochMs = if (completed) clock.millis() else null,
                tool = tool,
                urgeBefore = urgeBefore,
                urgeAfter = null,
                triggerTagsJson = triggerTags.sorted().joinToString(",", prefix = "[", postfix = "]") {
                    "\"${it.replace("\"", "")}\""
                },
                note = note.trim().ifEmpty { null },
                completed = completed,
                smokedAfter = null,
                externalRef = null,
            ),
        )
        return id
    }

    suspend fun startPause(urgeBefore: Int?, triggerTags: Set<String>, note: String): String {
        val now = clock.instant()
        val id = UUID.randomUUID().toString()
        dao.upsertUrgeSession(
            UrgeSessionEntity(
                id = id,
                startedAtEpochMs = now.toEpochMilli(),
                endedAtEpochMs = null,
                tool = "PAUSE",
                urgeBefore = urgeBefore?.coerceIn(1, 5),
                urgeAfter = null,
                triggerTagsJson = triggerTags.toTagsJson(),
                note = note.trim().take(500).ifEmpty { null },
                completed = false,
                smokedAfter = null,
                externalRef = null,
            ),
        )
        preferencesStore.updateData { current ->
            current.toBuilder()
                .setActivePauseSessionId(id)
                .setActivePauseStartedEpochMs(now.toEpochMilli())
                .setActivePauseEndEpochMs(now.plusSeconds(5 * 60).toEpochMilli())
                .clearActivePauseRemainingMs()
                .build()
        }
        return id
    }

    suspend fun pauseActivePause() {
        val now = clock.millis()
        preferencesStore.updateData { current ->
            if (current.activePauseSessionId.isBlank() || current.activePauseEndEpochMs <= 0) current
            else current.toBuilder()
                .setActivePauseRemainingMs((current.activePauseEndEpochMs - now).coerceAtLeast(1L))
                .clearActivePauseEndEpochMs()
                .build()
        }
    }

    suspend fun resumeActivePause() {
        val now = clock.millis()
        preferencesStore.updateData { current ->
            if (current.activePauseSessionId.isBlank() || current.activePauseRemainingMs <= 0) current
            else current.toBuilder()
                .setActivePauseEndEpochMs(now + current.activePauseRemainingMs)
                .clearActivePauseRemainingMs()
                .build()
        }
    }

    suspend fun cancelActivePause() {
        val pause = activePause.first()
        if (pause.sessionId.isNotBlank()) {
            dao.finishUrgeSession(pause.sessionId, clock.millis(), null, false, null)
        }
        clearActivePause()
    }

    suspend fun completeActivePause(): String? {
        val pause = activePause.first()
        if (pause.sessionId.isBlank()) return null
        dao.finishUrgeSession(pause.sessionId, clock.millis(), null, true, null)
        clearActivePause()
        evaluateAchievements()
        return pause.sessionId
    }

    suspend fun finishUrgeOutcome(sessionId: String, urgeAfter: Int?, smokedAfter: Boolean?) {
        dao.finishUrgeSession(
            id = sessionId,
            endedAtEpochMs = clock.millis(),
            urgeAfter = urgeAfter?.coerceIn(1, 5),
            completed = true,
            smokedAfter = smokedAfter,
        )
        evaluateAchievements()
    }

    suspend fun saveCompletedTool(
        tool: String,
        urgeBefore: Int?,
        urgeAfter: Int?,
        triggerTags: Set<String>,
        note: String,
        smokedAfter: Boolean?,
        externalRef: String? = null,
    ): String {
        val id = UUID.randomUUID().toString()
        val now = clock.instant()
        dao.upsertUrgeSession(
            UrgeSessionEntity(
                id = id,
                startedAtEpochMs = now.toEpochMilli(),
                endedAtEpochMs = now.toEpochMilli(),
                tool = tool.take(32),
                urgeBefore = urgeBefore?.coerceIn(1, 5),
                urgeAfter = urgeAfter?.coerceIn(1, 5),
                triggerTagsJson = triggerTags.toTagsJson(),
                note = note.trim().take(500).ifEmpty { null },
                completed = true,
                smokedAfter = smokedAfter,
                externalRef = externalRef?.take(64),
            ),
        )
        evaluateAchievements()
        return id
    }

    suspend fun saveAiSettings(enabled: Boolean, apiKey: String, model: String, proactiveNudges: Boolean) {
        val trimmedKey = apiKey.trim().take(MAX_API_KEY_CHARS)
        preferencesStore.updateData { current ->
            current.toBuilder()
                .setOllamaEnabled(enabled)
                .setOllamaModel(model.trim().take(96).ifBlank { OllamaClient.DEFAULT_MODEL })
                .setOllamaProactiveNudges(proactiveNudges)
                .apply {
                    if (trimmedKey.isNotEmpty()) {
                        ollamaApiKeyCiphertext = ByteString.copyFrom(SecretVault.encrypt(trimmedKey))
                    }
                }
                .build()
        }
    }

    /** Coach behaviour the user edits from the Coach screen. */
    suspend fun saveCoachBehaviour(
        systemPrompt: String,
        includeStats: Boolean,
        checkupsEnabled: Boolean,
        checkupIntervalMinutes: Int,
    ) {
        preferencesStore.updateData { current ->
            current.toBuilder()
                .setOllamaSystemPrompt(systemPrompt.trim().take(MAX_SYSTEM_PROMPT_CHARS))
                .setOllamaOmitStats(!includeStats)
                .setOllamaCheckupsEnabled(checkupsEnabled)
                .setOllamaCheckupIntervalMinutes(checkupIntervalMinutes.coerceIn(30, 24 * 60))
                .build()
        }
    }

    /**
     * True when an unprompted check-in is due: enabled, past the chosen interval, outside quiet
     * hours, and within the shared notification budget.
     */
    private fun partOfDay(hour: Int): String = when (hour) {
        in 5..8 -> "early morning"
        in 9..11 -> "late morning"
        in 12..13 -> "midday"
        in 14..17 -> "afternoon"
        in 18..21 -> "evening"
        else -> "late night"
    }

    /** Handles windows that wrap past midnight, e.g. 22:00 to 01:00. */
    private fun isInHighUrgeWindow(plan: PlanSettings, minuteOfDay: Int): Boolean {
        if (!plan.highUrgeWindowEnabled) return false
        val start = plan.highUrgeStartMinutes
        val end = plan.highUrgeEndMinutes
        return if (start <= end) minuteOfDay in start..end else minuteOfDay >= start || minuteOfDay <= end
    }

    suspend fun claimCheckup(): Boolean {
        val ai = aiSettings.first()
        if (!ai.isReady || !ai.checkupsEnabled) return false
        val plan = settings.first()
        if (!plan.onboardingCompleted) return false

        val now = clock.instant()
        val zone = zoneProvider()
        val date = now.atZone(zone).toLocalDate()
        val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val logs = dao.activeLogsBetween(start, end)
        val quietHours = PacingCalculator
            .calculate(now, zone, effectivePlan(plan), logs.map { it.toDomain() })
            .status is PacingStatus.Rest
        if (quietHours) return false

        var claimed = false
        preferencesStore.updateData { current ->
            val elapsed = now.toEpochMilli() - current.lastCheckupEpochMs
            val due = current.lastCheckupEpochMs <= 0L ||
                elapsed >= ai.checkupIntervalMinutes * 60_000L
            val dismissed = now.toEpochMilli() < current.notificationDismissedUntilEpochMs
            if (!due || dismissed) current else {
                claimed = true
                current.toBuilder().setLastCheckupEpochMs(now.toEpochMilli()).build()
            }
        }
        return claimed
    }

    suspend fun clearApiKey() {
        preferencesStore.updateData { current ->
            current.toBuilder()
                .clearOllamaApiKeyCiphertext()
                .setOllamaEnabled(false)
                .build()
        }
    }

    suspend fun appendCoachMessage(role: String, content: String): String {
        val id = UUID.randomUUID().toString()
        dao.upsertCoachMessage(
            CoachMessageEntity(
                id = id,
                createdAtEpochMs = clock.millis(),
                role = if (role == "user") "user" else "assistant",
                content = content.trim().take(MAX_COACH_MESSAGE_CHARS),
            ),
        )
        return id
    }

    suspend fun updateCoachMessage(id: String, content: String) {
        val existing = dao.allCoachMessages().firstOrNull { it.id == id } ?: return
        dao.upsertCoachMessage(existing.copy(content = content.take(MAX_COACH_MESSAGE_CHARS)))
    }

    suspend fun clearCoachMessages() = dao.deleteAllCoachMessages()

    /**
     * The widget fits roughly three short lines. Rather than clipping mid-word, fall back to the
     * first sentence, and give up entirely if even that is too long — a stale line beats a broken one.
     */
    internal fun fitToWidget(raw: String): String? {
        val cleaned = raw.trim().trim('"').replace(Regex("\\s+"), " ")
        if (cleaned.isEmpty()) return null
        if (cleaned.length <= MAX_WIDGET_QUOTE_CHARS) return cleaned
        val firstSentence = cleaned.split(Regex("(?<=[.!?])\\s"), limit = 2).first().trim()
        return firstSentence.takeIf { it.isNotEmpty() && it.length <= MAX_WIDGET_QUOTE_CHARS }
    }

    /** True when the widget's line is older than an hour and worth replacing. */
    suspend fun widgetQuoteIsStale(): Boolean {
        if (!aiSettings.first().isReady) return false
        val generated = widgetStore.data.first().quoteGeneratedEpochMs
        return clock.millis() - generated >= QUOTE_REFRESH_MS
    }

    suspend fun saveWidgetQuote(quote: String) {
        val trimmed = fitToWidget(quote) ?: return
        widgetStore.updateData { current ->
            current.toBuilder()
                .setQuote(trimmed)
                .setQuoteGeneratedEpochMs(clock.millis())
                .build()
        }
        PaceWidget().updateAll(context)
    }

    /** Builds the grounded snapshot the coach is allowed to reason about. */
    suspend fun coachContext(): CoachContext {
        val now = clock.instant()
        val zone = zoneProvider()
        val plan = settings.first()
        val logs = dao.allLogs().map { it.toDomain() }
        val sessions = dao.allUrgeSessions().map { it.toDomain() }
        val today = PacingCalculator.calculate(now, zone, effectivePlan(plan), logs)
        val quit = QuitProgress.calculate(
            now = now,
            zoneId = zone,
            logs = logs,
            baselinePerDay = plan.baselinePerDay,
            pricePerPack = plan.pricePerPack,
            cigarettesPerPack = plan.cigarettesPerPack,
            quitDate = plan.quitDate,
        )
        val lastLog = logs.filter { it.reversedAt == null }.maxByOrNull { it.occurredAt }?.occurredAt
        val zoned = now.atZone(zone)
        return CoachContext(
            countToday = today.count,
            ceiling = today.ceiling,
            minutesSinceLast = lastLog?.let { java.time.Duration.between(it, now).toMinutes() },
            minutesUntilNextWindow = (today.status as? PacingStatus.Spacing)
                ?.let { java.time.Duration.between(now, it.earliestWindow).toMinutes().coerceAtLeast(0) },
            zeroDayStreak = quit.zeroDayStreak,
            freeHours = quit.smokeFreeDuration.toHours(),
            avoided = quit.cigarettesAvoided,
            moneySaved = quit.moneySaved,
            currencyCode = plan.currencyCode,
            hardestSituations = sessions.flatMap { it.triggerTags }
                .groupingBy { it }
                .eachCount()
                .entries
                .sortedByDescending { it.value }
                .take(3)
                .map { it.key.replace('_', ' ') },
            personalReason = plan.personalReason,
            tone = plan.coachingTone.name,
            localTime = zoned.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")),
            dayOfWeek = zoned.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH),
            partOfDay = partOfDay(zoned.hour),
            timeZone = zone.id,
            inHighUrgeWindow = isInHighUrgeWindow(plan, zoned.hour * 60 + zoned.minute),
        )
    }

    /**
     * One-time import of an owner's pre-existing history and key into a private build.
     * Guarded by a preference flag so reinstalling over existing data never duplicates logs.
     */
    suspend fun applyProvisioning(
        apiKey: String,
        model: String,
        yesterdayCount: Int,
        todayCount: Int,
        lastTimeToday: java.time.LocalTime?,
        ceiling: Int,
        spacingMinutes: Int,
    ) {
        if (preferencesStore.data.first().provisioningSeedApplied) return

        val zone = zoneProvider()
        val today = clock.instant().atZone(zone).toLocalDate()
        val yesterday = today.minusDays(1)
        val basePlan = settings.first()
        val plan = basePlan.copy(
            onboardingCompleted = true,
            dailyCeiling = ceiling.takeIf { it > 0 } ?: basePlan.dailyCeiling,
            baselinePerDay = maxOf(yesterdayCount, ceiling, basePlan.baselinePerDay),
            minimumGapMinutes = spacingMinutes.takeIf { it > 0 } ?: basePlan.minimumGapMinutes,
            adaptiveSpacingEnabled = true,
        )

        val dayStart = java.time.LocalTime.of(plan.wakeMinutes / 60, plan.wakeMinutes % 60)
        val entries = buildList {
            ProvisioningSeeder.timesFor(yesterdayCount, yesterday, null, dayStart)
                .forEach { add(yesterday.atTime(it)) }
            ProvisioningSeeder.timesFor(todayCount, today, lastTimeToday, dayStart)
                .forEach { add(today.atTime(it)) }
        }

        database.withTransaction {
            listOf(yesterday, today).forEach { date ->
                dao.insertDailySnapshot(
                    DailyPlanSnapshotEntity(
                        localDate = date.toString(),
                        zoneId = zone.id,
                        baseline = plan.baselinePerDay,
                        ceiling = plan.dailyCeiling,
                        minimumGapMinutes = plan.minimumGapMinutes,
                        wakeMinutes = plan.wakeMinutes,
                        sleepMinutes = plan.sleepMinutes,
                        morningHoldMinutes = plan.morningHoldMinutes,
                        flexibleDay = plan.flexibleDay,
                        createdAtEpochMs = date.atStartOfDay(zone).toInstant().toEpochMilli(),
                    ),
                )
            }
            entries.forEach { moment ->
                val epochMs = moment.atZone(zone).toInstant().toEpochMilli()
                dao.insertLog(
                    CigaretteLogEntity(
                        id = UUID.randomUUID().toString(),
                        occurredAtEpochMs = epochMs,
                        recordedAtEpochMs = epochMs,
                        source = "IMPORT",
                        note = null,
                        reversedAtEpochMs = null,
                        reversalReason = null,
                    ),
                )
            }
        }

        val trimmedKey = apiKey.trim().take(MAX_API_KEY_CHARS)
        preferencesStore.updateData { current ->
            current.withPlan(plan).toBuilder()
                .setOnboardingCompleted(true)
                .setProvisioningSeedApplied(true)
                .apply {
                    if (trimmedKey.isNotEmpty()) {
                        ollamaApiKeyCiphertext = ByteString.copyFrom(SecretVault.encrypt(trimmedKey))
                        ollamaEnabled = true
                        ollamaProactiveNudges = true
                        ollamaCheckupsEnabled = true
                        ollamaCheckupIntervalMinutes = 180
                        ollamaModel = model.trim().ifBlank { OllamaClient.DEFAULT_MODEL }
                    }
                }
                .build()
        }
        evaluateAchievements()
        refreshWidgetSnapshot()
    }

    suspend fun startExternalBreak(gameId: String): String {
        val id = UUID.randomUUID().toString()
        dao.upsertExternalBreak(ExternalBreakEntity(id, gameId.take(64), clock.millis(), null, null))
        return id
    }

    suspend fun finishExternalBreak(id: String, gameId: String, startedAtEpochMs: Long) {
        dao.upsertExternalBreak(ExternalBreakEntity(id, gameId.take(64), startedAtEpochMs, clock.millis(), null))
    }

    suspend fun deleteAllData() {
        database.clearAllTables()
        preferencesStore.updateData { PacePreferences.getDefaultInstance() }
        widgetStore.updateData { WidgetSnapshot.getDefaultInstance() }
        WorkManager.getInstance(context).cancelAllWorkByTag("pace")
        context.getSystemService(NotificationManager::class.java).cancelAll()
        PaceWidget().updateAll(context)
    }

    suspend fun exportJson(): String {
        val plan = settings.first()
        val backup = PaceBackup(
            exportedAtEpochMs = clock.millis(),
            plan = PlanBackup(
                baselinePerDay = plan.baselinePerDay,
                dailyCeiling = plan.dailyCeiling,
                minimumGapMinutes = plan.minimumGapMinutes,
                wakeMinutes = plan.wakeMinutes,
                sleepMinutes = plan.sleepMinutes,
                weekendWakeEnabled = plan.weekendWakeEnabled,
                weekendWakeMinutes = plan.weekendWakeMinutes,
                morningHoldMinutes = plan.morningHoldMinutes,
                flexibleDay = plan.flexibleDay,
                reductionStep = plan.reductionStep,
                reviewIntervalDays = plan.reviewIntervalDays,
                pricePerPack = plan.pricePerPack,
                cigarettesPerPack = plan.cigarettesPerPack,
                currencyCode = plan.currencyCode,
                personalReason = plan.personalReason,
                rewardName = plan.rewardName,
                rewardTarget = plan.rewardTarget,
                coachingTone = plan.coachingTone.name,
                reminderIntensity = plan.reminderIntensity.name,
                notificationPrivate = plan.notificationPrivate,
                hapticsEnabled = plan.hapticsEnabled,
                themeMode = plan.themeMode.name,
            ),
            logs = dao.allLogs().map {
                LogBackup(it.id, it.occurredAtEpochMs, it.recordedAtEpochMs, it.source, it.note, it.reversedAtEpochMs, it.reversalReason)
            },
            urgeSessions = dao.allUrgeSessions().map {
                UrgeBackup(
                    it.id, it.startedAtEpochMs, it.endedAtEpochMs, it.tool, it.urgeBefore, it.urgeAfter,
                    it.triggerTagsJson, it.note, it.completed, it.smokedAfter, it.externalRef,
                )
            },
            dailySnapshots = dao.allDailySnapshots().map {
                SnapshotBackup(
                    it.localDate, it.zoneId, it.baseline, it.ceiling, it.minimumGapMinutes, it.wakeMinutes,
                    it.sleepMinutes, it.morningHoldMinutes, it.flexibleDay, it.createdAtEpochMs,
                )
            },
            achievements = dao.allAchievements().map { AchievementBackup(it.badgeId, it.unlockedAtEpochMs, it.evidenceJson) },
            externalBreaks = dao.allExternalBreaks().map {
                ExternalBreakBackup(it.id, it.gameId, it.startedAtEpochMs, it.returnedAtEpochMs, it.urgeSessionId)
            },
        )
        return backupJson.encodeToString(PaceBackup.serializer(), backup)
    }

    suspend fun importJson(jsonText: String) {
        require(jsonText.length <= MAX_IMPORT_CHARS) { "Backup is too large" }
        val backup = backupJson.decodeFromString(PaceBackup.serializer(), jsonText)
        validateBackup(backup)
        database.withTransaction {
            dao.deleteAllLogs()
            dao.deleteAllUrgeSessions()
            dao.deleteAllDailySnapshots()
            dao.deleteAllAchievements()
            dao.deleteAllPersonalRecords()
            dao.deleteAllExternalBreaks()
            dao.deleteAllCoachMessages()
            backup.logs.forEach {
                dao.insertLog(CigaretteLogEntity(it.id, it.occurredAtEpochMs, it.recordedAtEpochMs, "IMPORT", it.note, it.reversedAtEpochMs, it.reversalReason))
            }
            backup.urgeSessions.forEach {
                dao.upsertUrgeSession(
                    UrgeSessionEntity(
                        it.id, it.startedAtEpochMs, it.endedAtEpochMs, it.tool, it.urgeBefore, it.urgeAfter,
                        it.triggerTagsJson, it.note, it.completed, it.smokedAfter, it.externalRef,
                    ),
                )
            }
            backup.dailySnapshots.forEach {
                dao.insertDailySnapshot(
                    DailyPlanSnapshotEntity(
                        it.localDate, it.zoneId, it.baseline, it.ceiling, it.minimumGapMinutes,
                        it.wakeMinutes, it.sleepMinutes, it.morningHoldMinutes, it.flexibleDay, it.createdAtEpochMs,
                    ),
                )
            }
            backup.achievements.forEach { dao.insertAchievement(AchievementEntity(it.badgeId, it.unlockedAtEpochMs, it.evidenceJson)) }
            backup.externalBreaks.forEach {
                dao.upsertExternalBreak(ExternalBreakEntity(it.id, it.gameId, it.startedAtEpochMs, it.returnedAtEpochMs, it.urgeSessionId))
            }
        }
        preferencesStore.updateData { current ->
            current.withPlan(backup.plan.toDomain()).toBuilder()
                .setOnboardingCompleted(true)
                .clearPendingDailyCeiling()
                .clearPendingCeilingEffectiveDate()
                .clearActivePauseSessionId()
                .clearActivePauseEndEpochMs()
                .clearActivePauseRemainingMs()
                .build()
        }
        refreshWidgetSnapshot()
    }

    suspend fun isQuietHoursNow(): Boolean {
        val now = clock.instant()
        val zone = zoneProvider()
        val date = now.atZone(zone).toLocalDate()
        val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val plan = settings.first()
        val logs = dao.activeLogsBetween(start, end).map { it.toDomain() }
        return PacingCalculator.calculate(now, zone, effectivePlan(plan), logs).status is PacingStatus.Rest
    }

    private fun validateBackup(backup: PaceBackup) {
        PaceBackupValidator.validate(backup, clock.millis())
        require(backup.schemaVersion == 1) { "Unsupported backup schema" }
        val plan = backup.plan
        require(plan.baselinePerDay in 1..100 && plan.dailyCeiling in 0..100)
        require(plan.minimumGapMinutes in 15..360 && plan.morningHoldMinutes in 0..240)
        require(plan.wakeMinutes in 0..1439 && plan.sleepMinutes in 0..1439 && plan.wakeMinutes != plan.sleepMinutes)
        require(plan.weekendWakeMinutes in 0..1439 && plan.reductionStep in 1..5 && plan.reviewIntervalDays in 7..28)
        require(plan.cigarettesPerPack in 1..100 && plan.pricePerPack >= 0 && plan.rewardTarget >= 0)
        require(plan.currencyCode.length == 3 && plan.personalReason.length <= 500 && plan.rewardName.length <= 100)
        require(runCatching { CoachingTone.valueOf(plan.coachingTone) }.isSuccess)
        require(runCatching { ReminderIntensity.valueOf(plan.reminderIntensity) }.isSuccess)
        require(runCatching { ThemeMode.valueOf(plan.themeMode) }.isSuccess)
        require(backup.logs.map { it.id }.size == backup.logs.map { it.id }.distinct().size)
        require(backup.urgeSessions.map { it.id }.size == backup.urgeSessions.map { it.id }.distinct().size)
        require(backup.dailySnapshots.map { it.localDate }.size == backup.dailySnapshots.map { it.localDate }.distinct().size)
        val latestAllowed = clock.millis() + 24 * 60 * 60 * 1_000L
        backup.logs.forEach {
            require(it.id.isNotBlank() && it.occurredAtEpochMs in 0..latestAllowed && it.recordedAtEpochMs in 0..latestAllowed)
            require(it.note?.length ?: 0 <= 500 && it.source in setOf("APP", "WIDGET", "IMPORT"))
        }
        backup.urgeSessions.forEach {
            require(it.id.isNotBlank() && it.startedAtEpochMs in 0..latestAllowed)
            require(it.urgeBefore == null || it.urgeBefore in 1..5)
            require(it.urgeAfter == null || it.urgeAfter in 1..5)
            require(it.note?.length ?: 0 <= 500 && it.triggerTagsJson.length <= 1_000)
        }
        backup.dailySnapshots.forEach {
            require(runCatching { LocalDate.parse(it.localDate) }.isSuccess)
            require(it.baseline in 1..100 && it.ceiling in 0..100 && it.minimumGapMinutes in 15..360)
        }
    }

    private fun PlanBackup.toDomain() = PlanSettings(
        onboardingCompleted = true,
        baselinePerDay = baselinePerDay,
        dailyCeiling = dailyCeiling,
        minimumGapMinutes = minimumGapMinutes,
        wakeMinutes = wakeMinutes,
        sleepMinutes = sleepMinutes,
        weekendWakeEnabled = weekendWakeEnabled,
        weekendWakeMinutes = weekendWakeMinutes,
        morningHoldMinutes = morningHoldMinutes,
        flexibleDay = flexibleDay,
        reductionStep = reductionStep,
        reviewIntervalDays = reviewIntervalDays,
        pricePerPack = pricePerPack,
        cigarettesPerPack = cigarettesPerPack,
        currencyCode = currencyCode,
        personalReason = personalReason,
        rewardName = rewardName,
        rewardTarget = rewardTarget,
        coachingTone = CoachingTone.valueOf(coachingTone),
        reminderIntensity = ReminderIntensity.valueOf(reminderIntensity),
        notificationPrivate = notificationPrivate,
        hapticsEnabled = hapticsEnabled,
        themeMode = ThemeMode.valueOf(themeMode),
    )

    suspend fun refreshWidgetSnapshot(undoLogId: String = "") {
        val now = clock.instant()
        val zone = zoneProvider()
        val date = now.atZone(zone).toLocalDate()
        val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val logs = dao.activeLogsBetween(start, end)
        val plan = settings.first()
        ensureTodaySnapshot(plan)
        val summary = PacingCalculator.calculate(
            now = now,
            zoneId = zone,
            settings = effectivePlan(plan),
            logs = logs.map { it.toDomain() },
        )
        val (widgetState, stateUntil) = when (val status = summary.status) {
            is PacingStatus.Spacing -> WidgetStateProto.WIDGET_STATE_SPACING to status.earliestWindow.toInstant()
            is PacingStatus.WindowMet -> WidgetStateProto.WIDGET_STATE_WINDOW_MET to status.since.toInstant()
            is PacingStatus.MorningHold -> WidgetStateProto.WIDGET_STATE_MORNING_HOLD to status.until.toInstant()
            is PacingStatus.Rest -> WidgetStateProto.WIDGET_STATE_REST to status.nextWake.toInstant()
            PacingStatus.CeilingReached -> WidgetStateProto.WIDGET_STATE_CEILING to null
            PacingStatus.Recovery -> WidgetStateProto.WIDGET_STATE_RECOVERY to null
        }
        val safeMessage = when (summary.status) {
            is PacingStatus.Rest -> context.getString(R.string.widget_message_rest)
            PacingStatus.Recovery -> context.getString(R.string.widget_message_recovery)
            PacingStatus.CeilingReached -> context.getString(R.string.widget_message_ceiling)
            else -> context.getString(R.string.widget_message_default)
        }
        val badges = dao.allAchievements()
        val quit = QuitProgress.calculate(
            now = now,
            zoneId = zone,
            logs = dao.allLogs().map { it.toDomain() },
            baselinePerDay = plan.baselinePerDay,
            pricePerPack = plan.pricePerPack,
            cigarettesPerPack = plan.cigarettesPerPack,
            quitDate = plan.quitDate,
        )
        widgetStore.updateData { previous ->
            WidgetSnapshot.newBuilder()
                // The rotating line has its own hourly cadence; a state refresh must not drop it.
                .setQuote(previous.quote)
                .setQuoteGeneratedEpochMs(previous.quoteGeneratedEpochMs)
                .setLatestBadgeId(badges.maxByOrNull { it.unlockedAtEpochMs }?.badgeId.orEmpty())
                .setBadgeCount(badges.size)
                .setMinimumGapMinutes(effectivePlan(plan).minimumGapMinutes)
                .setSmokeFreeMinutes(quit.smokeFreeDuration.toMinutes().toInt())
                .setCigarettesAvoided(quit.cigarettesAvoided)
                .setMoneySaved(quit.moneySaved)
                .setCurrencyCode(plan.currencyCode)
                .setZeroDayStreak(quit.zeroDayStreak)
                .setGeneratedAtEpochMs(now.toEpochMilli())
                .setLocalDate(date.toString())
                .setCountToday(logs.size)
                .setCeiling(plan.dailyCeiling)
                .setLastActiveLogEpochMs(logs.lastOrNull()?.occurredAtEpochMs ?: 0L)
                .setState(widgetState)
                .setStateUntilEpochMs(stateUntil?.toEpochMilli() ?: 0L)
                .setSafeMessage(safeMessage)
                .setUndoLogId(undoLogId)
                .setUndoExpiryEpochMs(if (undoLogId.isEmpty()) 0L else now.plusSeconds(12).toEpochMilli())
                .build()
        }
        PaceWidget().updateAll(context)
        // Flip the widget's countdown exactly when this window ends.
        WidgetBoundaryWorker.scheduleAt(context, stateUntil?.toEpochMilli() ?: 0L)
    }

    suspend fun claimCoachingNotification(): Boolean {
        val now = clock.instant()
        val zone = zoneProvider()
        val plan = settings.first()
        if (!plan.onboardingCompleted || plan.reminderIntensity == ReminderIntensity.OFF) return false
        val date = now.atZone(zone).toLocalDate()
        val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val logs = dao.activeLogsBetween(start, end)
        val quietHours = PacingCalculator.calculate(now, zone, effectivePlan(plan), logs.map { it.toDomain() }).status is PacingStatus.Rest
        val latestLog = logs.lastOrNull()?.occurredAtEpochMs ?: 0L
        var claimed = false
        preferencesStore.updateData { current ->
            val countToday = if (current.notificationCountLocalDate == date.toString()) current.notificationCountToday else 0
            val maximum = if (plan.reminderIntensity == ReminderIntensity.GENTLE) 2 else 4
            val eligible = NotificationPolicy.isEligible(
                nowEpochMs = now.toEpochMilli(),
                quietHours = quietHours,
                countToday = countToday,
                dailyMaximum = maximum,
                lastNotificationEpochMs = current.lastCoachingNotificationEpochMs,
                dismissedUntilEpochMs = current.notificationDismissedUntilEpochMs,
                latestLogEpochMs = latestLog,
            )
            if (!eligible) current else {
                claimed = true
                current.toBuilder()
                    .setNotificationCountLocalDate(date.toString())
                    .setNotificationCountToday(countToday + 1)
                    .setLastCoachingNotificationEpochMs(now.toEpochMilli())
                    .build()
            }
        }
        return claimed
    }

    /**
     * True when an AI nudge is warranted: the coach is on, the user asked for nudges, their next
     * planned window is close, and the shared notification rate limit allows one.
     */
    suspend fun claimProactiveNudge(): Boolean {
        val ai = aiSettings.first()
        if (!ai.isReady || !ai.proactiveNudges) return false
        val plan = settings.first()
        if (!plan.onboardingCompleted) return false

        val now = clock.instant()
        val zone = zoneProvider()
        val date = now.atZone(zone).toLocalDate()
        val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val logs = dao.activeLogsBetween(start, end)
        val summary = PacingCalculator.calculate(now, zone, effectivePlan(plan), logs.map { it.toDomain() })

        val minutesToWindow = (summary.status as? PacingStatus.Spacing)
            ?.let { java.time.Duration.between(now, it.earliestWindow).toMinutes() }
            ?: return false
        if (minutesToWindow !in 1..NUDGE_LEAD_MINUTES) return false

        var claimed = false
        preferencesStore.updateData { current ->
            val countToday = if (current.notificationCountLocalDate == date.toString()) {
                current.notificationCountToday
            } else {
                0
            }
            val eligible = NotificationPolicy.isEligible(
                nowEpochMs = now.toEpochMilli(),
                quietHours = summary.status is PacingStatus.Rest,
                countToday = countToday,
                dailyMaximum = NUDGE_DAILY_MAXIMUM,
                lastNotificationEpochMs = current.lastCoachingNotificationEpochMs,
                dismissedUntilEpochMs = current.notificationDismissedUntilEpochMs,
                latestLogEpochMs = logs.lastOrNull()?.occurredAtEpochMs ?: 0L,
            )
            if (!eligible) current else {
                claimed = true
                current.toBuilder()
                    .setNotificationCountLocalDate(date.toString())
                    .setNotificationCountToday(countToday + 1)
                    .setLastCoachingNotificationEpochMs(now.toEpochMilli())
                    .build()
            }
        }
        return claimed
    }

    suspend fun dismissCoachingNotifications() {
        preferencesStore.updateData { current ->
            current.toBuilder()
                .setNotificationDismissedUntilEpochMs(clock.instant().plusSeconds(90 * 60).toEpochMilli())
                .build()
        }
    }

    suspend fun evaluateStoredAchievements() = evaluateAchievements()

    /**
     * The plan pacing should actually run with: identical to [plan] unless adaptive spacing is on,
     * in which case the minimum gap reflects the steady days earned so far.
     */
    suspend fun effectivePlan(plan: PlanSettings): PlanSettings {
        if (!plan.adaptiveSpacingEnabled) return plan
        return AdaptiveSpacing.applyTo(plan, progressDays(plan))
    }

    suspend fun spacingProgress(): SpacingProgress {
        val plan = settings.first()
        return AdaptiveSpacing.progress(plan, progressDays(plan))
    }

    private suspend fun progressDays(plan: PlanSettings): List<DailyProgress> =
        ProgressCalculator.calculate(
            today = clock.instant().atZone(zoneProvider()).toLocalDate(),
            zoneId = zoneProvider(),
            logs = dao.allLogs().map { it.toDomain() },
            snapshots = dao.allDailySnapshots().map { it.toDomain() },
            sessions = emptyList(),
            pricePerPack = plan.pricePerPack,
            cigarettesPerPack = plan.cigarettesPerPack,
            rewardTarget = plan.rewardTarget,
        ).days

    private suspend fun ensureTodaySnapshot(plan: PlanSettings) =
        ensureSnapshotFor(LocalDate.now(clock.withZone(zoneProvider())), plan)

    private suspend fun ensureSnapshotFor(date: LocalDate, plan: PlanSettings) {
        val zone = zoneProvider()
        dao.insertDailySnapshot(
            DailyPlanSnapshotEntity(
                localDate = date.toString(),
                zoneId = zone.id,
                baseline = plan.baselinePerDay,
                ceiling = plan.dailyCeiling,
                minimumGapMinutes = plan.minimumGapMinutes,
                wakeMinutes = plan.wakeMinutes,
                sleepMinutes = plan.sleepMinutes,
                morningHoldMinutes = plan.morningHoldMinutes,
                flexibleDay = plan.flexibleDay,
                createdAtEpochMs = clock.millis(),
            ),
        )
    }

    private fun PacePreferences.withPlan(plan: PlanSettings): PacePreferences = toBuilder()
        .setSchemaVersion(1)
        .setBaselinePerDay(plan.baselinePerDay.coerceIn(1, 100))
        .setDailyCeiling(plan.dailyCeiling.coerceIn(0, 100))
        .setMinimumGapMinutes(plan.minimumGapMinutes.coerceIn(15, 360))
        .setWakeMinutes(plan.wakeMinutes.coerceIn(0, 1439))
        .setSleepMinutes(plan.sleepMinutes.coerceIn(0, 1439))
        .setWeekendWakeEnabled(plan.weekendWakeEnabled)
        .setWeekendWakeMinutes(plan.weekendWakeMinutes.coerceIn(0, 1439))
        .setMorningHoldMinutes(plan.morningHoldMinutes.coerceIn(0, 240))
        .setFlexibleDay(plan.flexibleDay)
        .setReductionStep(plan.reductionStep.coerceIn(1, 5))
        .setReviewIntervalDays(plan.reviewIntervalDays.coerceIn(7, 28))
        .setPricePerPack(plan.pricePerPack.coerceAtLeast(0.0))
        .setCigarettesPerPack(plan.cigarettesPerPack.coerceIn(1, 100))
        .setCurrencyCode(plan.currencyCode.take(3).uppercase())
        .setPersonalReason(plan.personalReason.take(500))
        .setRewardName(plan.rewardName.take(100))
        .setRewardTarget(plan.rewardTarget.coerceAtLeast(0.0))
        .setCoachingTone(plan.coachingTone.toProto())
        .setReminderIntensity(plan.reminderIntensity.toProto())
        .setNotificationPrivate(plan.notificationPrivate)
        .setHapticsEnabled(plan.hapticsEnabled)
        .setThemeMode(plan.themeMode.toProto())
        .setQuitMode(plan.quitMode)
        .setQuitDate(plan.quitDate?.toString().orEmpty())
        .setAdaptiveSpacingEnabled(plan.adaptiveSpacingEnabled)
        .setAdaptiveSpacingStepMinutes(plan.adaptiveSpacingStepMinutes.coerceIn(5, 60))
        .setAdaptiveSpacingIntervalDays(plan.adaptiveSpacingIntervalDays.coerceIn(1, 30))
        .setAdaptiveSpacingMaxMinutes(plan.adaptiveSpacingMaxMinutes.coerceIn(30, 720))
        .setHighUrgeWindowEnabled(plan.highUrgeWindowEnabled)
        .setHighUrgeStartMinutes(plan.highUrgeStartMinutes.coerceIn(0, 1439))
        .setHighUrgeEndMinutes(plan.highUrgeEndMinutes.coerceIn(0, 1439))
        .build()

    private fun toDomain(proto: PacePreferences): PlanSettings = PlanSettings(
        onboardingCompleted = proto.onboardingCompleted,
        baselinePerDay = proto.baselinePerDay.takeIf { it > 0 } ?: 12,
        dailyCeiling = proto.effectiveDailyCeiling(),
        minimumGapMinutes = proto.minimumGapMinutes.takeIf { it > 0 } ?: 90,
        wakeMinutes = proto.wakeMinutes,
        sleepMinutes = proto.sleepMinutes.takeIf { it > 0 } ?: 22 * 60 + 30,
        weekendWakeEnabled = proto.weekendWakeEnabled,
        weekendWakeMinutes = proto.weekendWakeMinutes.takeIf { it > 0 } ?: 8 * 60,
        morningHoldMinutes = proto.morningHoldMinutes,
        flexibleDay = proto.flexibleDay,
        reductionStep = proto.reductionStep.takeIf { it > 0 } ?: 1,
        reviewIntervalDays = proto.reviewIntervalDays.takeIf { it > 0 } ?: 7,
        pricePerPack = proto.pricePerPack,
        cigarettesPerPack = proto.cigarettesPerPack.takeIf { it > 0 } ?: 20,
        currencyCode = proto.currencyCode.ifBlank { "DKK" },
        personalReason = proto.personalReason,
        rewardName = proto.rewardName,
        rewardTarget = proto.rewardTarget,
        coachingTone = when (proto.coachingTone) {
            CoachingToneProto.COACHING_TONE_DIRECT -> CoachingTone.DIRECT
            CoachingToneProto.COACHING_TONE_TOUGH -> CoachingTone.TOUGH
            else -> CoachingTone.SUPPORTIVE
        },
        reminderIntensity = when (proto.reminderIntensity) {
            ReminderIntensityProto.REMINDER_INTENSITY_GENTLE -> ReminderIntensity.GENTLE
            ReminderIntensityProto.REMINDER_INTENSITY_STANDARD -> ReminderIntensity.STANDARD
            else -> ReminderIntensity.OFF
        },
        notificationPrivate = proto.notificationPrivate,
        hapticsEnabled = proto.hapticsEnabled,
        themeMode = when (proto.themeMode) {
            ThemeModeProto.THEME_MODE_LIGHT -> ThemeMode.LIGHT
            ThemeModeProto.THEME_MODE_DARK -> ThemeMode.DARK
            else -> ThemeMode.SYSTEM
        },
        quitMode = proto.quitMode,
        quitDate = proto.quitDate.takeIf(String::isNotBlank)
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
        adaptiveSpacingEnabled = proto.adaptiveSpacingEnabled,
        adaptiveSpacingStepMinutes = proto.adaptiveSpacingStepMinutes.takeIf { it > 0 } ?: 15,
        adaptiveSpacingIntervalDays = proto.adaptiveSpacingIntervalDays.takeIf { it > 0 } ?: 7,
        adaptiveSpacingMaxMinutes = proto.adaptiveSpacingMaxMinutes.takeIf { it > 0 } ?: 240,
        highUrgeWindowEnabled = proto.highUrgeWindowEnabled,
        highUrgeStartMinutes = proto.highUrgeStartMinutes.takeIf { it > 0 } ?: (15 * 60),
        highUrgeEndMinutes = proto.highUrgeEndMinutes.takeIf { it > 0 } ?: (18 * 60),
    )

    private fun PacePreferences.effectiveDailyCeiling(): Int {
        val effectiveDate = pendingCeilingEffectiveDate
            .takeIf(String::isNotBlank)
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        return if (effectiveDate != null && !LocalDate.now(clock.withZone(zoneProvider())).isBefore(effectiveDate)) {
            pendingDailyCeiling
        } else {
            dailyCeiling
        }
    }

    private fun CoachingTone.toProto(): CoachingToneProto = when (this) {
        CoachingTone.SUPPORTIVE -> CoachingToneProto.COACHING_TONE_SUPPORTIVE
        CoachingTone.DIRECT -> CoachingToneProto.COACHING_TONE_DIRECT
        CoachingTone.TOUGH -> CoachingToneProto.COACHING_TONE_TOUGH
    }

    private fun ReminderIntensity.toProto(): ReminderIntensityProto = when (this) {
        ReminderIntensity.OFF -> ReminderIntensityProto.REMINDER_INTENSITY_OFF
        ReminderIntensity.GENTLE -> ReminderIntensityProto.REMINDER_INTENSITY_GENTLE
        ReminderIntensity.STANDARD -> ReminderIntensityProto.REMINDER_INTENSITY_STANDARD
    }

    private fun ThemeMode.toProto(): ThemeModeProto = when (this) {
        ThemeMode.SYSTEM -> ThemeModeProto.THEME_MODE_SYSTEM
        ThemeMode.LIGHT -> ThemeModeProto.THEME_MODE_LIGHT
        ThemeMode.DARK -> ThemeModeProto.THEME_MODE_DARK
    }

    private fun CigaretteLogEntity.toDomain(): CigaretteLog = CigaretteLog(
        id = id,
        occurredAt = Instant.ofEpochMilli(occurredAtEpochMs),
        recordedAt = Instant.ofEpochMilli(recordedAtEpochMs),
        source = source,
        reversedAt = reversedAtEpochMs?.let(Instant::ofEpochMilli),
    )

    private fun DailyPlanSnapshotEntity.toDomain() = DailyPlanSnapshot(
        localDate = LocalDate.parse(localDate),
        baseline = baseline,
        ceiling = ceiling,
        minimumGapMinutes = minimumGapMinutes,
        wakeMinutes = wakeMinutes,
        morningHoldMinutes = morningHoldMinutes,
    )

    private fun com.pace.reduction.data.db.AchievementEntity.toDomain() = Achievement(
        badgeId = badgeId,
        unlockedAt = Instant.ofEpochMilli(unlockedAtEpochMs),
        evidence = evidenceJson,
    )

    private fun UrgeSessionEntity.toDomain() = UrgeSession(
        id = id,
        startedAt = Instant.ofEpochMilli(startedAtEpochMs),
        endedAt = endedAtEpochMs?.let(Instant::ofEpochMilli),
        tool = tool,
        urgeBefore = urgeBefore,
        urgeAfter = urgeAfter,
        triggerTags = triggerTagsJson.removePrefix("[").removeSuffix("]")
            .split(',')
            .map { it.trim().removeSurrounding("\"") }
            .filter(String::isNotBlank)
            .toSet(),
        note = note,
        completed = completed,
        smokedAfter = smokedAfter,
        externalRef = externalRef,
    )

    private suspend fun evaluateAchievements() {
        val now = clock.instant()
        val zone = zoneProvider()
        val logs = dao.allLogs().map { it.toDomain() }
        val snapshots = dao.allDailySnapshots().map { it.toDomain() }
        val sessions = dao.allUrgeSessions().map { it.toDomain() }
        val plan = settings.first()
        val metrics = ProgressCalculator.calculate(
            today = now.atZone(zone).toLocalDate(),
            zoneId = zone,
            logs = logs,
            snapshots = snapshots,
            sessions = sessions,
            pricePerPack = plan.pricePerPack,
            cigarettesPerPack = plan.cigarettesPerPack,
            rewardTarget = plan.rewardTarget,
        )
        val quit = QuitProgress.calculate(
            now = now,
            zoneId = zone,
            logs = logs,
            baselinePerDay = plan.baselinePerDay,
            pricePerPack = plan.pricePerPack,
            cigarettesPerPack = plan.cigarettesPerPack,
            quitDate = plan.quitDate,
        )
        val conversations = dao.allCoachMessages().count { it.role == "user" }
        BadgeEngine.eligible(metrics, logs, snapshots, sessions, zone, quit, conversations).forEach { candidate ->
            dao.insertAchievement(
                AchievementEntity(
                    badgeId = candidate.id,
                    unlockedAtEpochMs = now.toEpochMilli(),
                    evidenceJson = candidate.evidence,
                ),
            )
        }
    }

    private suspend fun clearActivePause() {
        preferencesStore.updateData { current ->
            current.toBuilder()
                .clearActivePauseSessionId()
                .clearActivePauseStartedEpochMs()
                .clearActivePauseEndEpochMs()
                .clearActivePauseRemainingMs()
                .build()
        }
    }

    private fun Set<String>.toTagsJson(): String = sorted()
        .joinToString(",", prefix = "[", postfix = "]") { "\"${it.replace("\"", "")}\"" }


    private fun CoachMessageEntity.toDomain() = CoachMessage(
        id = id,
        createdAt = Instant.ofEpochMilli(createdAtEpochMs),
        role = role,
        content = content,
    )

    private companion object {
        const val MAX_IMPORT_CHARS = 1_000_000
        const val MAX_API_KEY_CHARS = 256
        const val MAX_COACH_MESSAGE_CHARS = 4_000
        const val MAX_SYSTEM_PROMPT_CHARS = 2_000

        const val QUOTE_REFRESH_MS = 60 * 60 * 1_000L
        const val MAX_WIDGET_QUOTE_CHARS = 90

        /** Fire a nudge when the next planned window is this close. */
        const val NUDGE_LEAD_MINUTES = 20L
        const val NUDGE_DAILY_MAXIMUM = 4
    }
}
