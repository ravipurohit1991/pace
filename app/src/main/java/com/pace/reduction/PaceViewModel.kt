package com.pace.reduction

import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import com.pace.reduction.core.coach.CoachService
import com.pace.reduction.core.network.OllamaMessage
import com.pace.reduction.data.repository.PaceRepository
import com.pace.reduction.domain.PacingCalculator
import com.pace.reduction.domain.CoachImageMemory
import com.pace.reduction.domain.ProgressCalculator
import com.pace.reduction.domain.ProgressMetrics
import com.pace.reduction.domain.AdaptiveSpacing
import com.pace.reduction.domain.QuitMetrics
import com.pace.reduction.core.steps.StepSensor
import com.pace.reduction.domain.QuitProgress
import com.pace.reduction.domain.StepCalculator
import com.pace.reduction.domain.StepDay
import com.pace.reduction.domain.StepMetrics
import com.pace.reduction.domain.SpacingProgress
import com.pace.reduction.domain.UrgePattern
import com.pace.reduction.domain.UrgePatterns
import com.pace.reduction.domain.WithdrawalStatus
import com.pace.reduction.domain.WithdrawalTimeline
import com.pace.reduction.data.repository.PaceRepository.Companion.MOVE_TOOL_PREFIX
import com.pace.reduction.domain.model.ActivePause
import com.pace.reduction.domain.model.Achievement
import com.pace.reduction.domain.model.AiSettings
import com.pace.reduction.domain.model.CigaretteLog
import com.pace.reduction.domain.model.CoachMessage
import com.pace.reduction.domain.model.DailyCount
import com.pace.reduction.domain.model.PlanSettings
import com.pace.reduction.domain.model.TodaySummary
import com.pace.reduction.domain.model.UrgeSession
import com.pace.reduction.domain.model.WidgetSettings
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PaceUiState(
    val settings: PlanSettings = PlanSettings(),
    val today: TodaySummary? = null,
    val activeLogs: List<CigaretteLog> = emptyList(),
    val dailyCounts: List<DailyCount> = emptyList(),
    val urgeSessionCount: Int = 0,
    val urgeSessions: List<UrgeSession> = emptyList(),
    val progress: ProgressMetrics? = null,
    val quit: QuitMetrics? = null,
    val spacing: SpacingProgress? = null,
    val achievements: List<Achievement> = emptyList(),
    val activePause: ActivePause = ActivePause(),
    val widget: WidgetSettings = WidgetSettings(),
    val ai: AiSettings = AiSettings(),
    val coachMessages: List<CoachMessage> = emptyList(),
    val steps: StepMetrics = StepMetrics(),
    /** What the log timestamps say about when the day is hardest, recomputed with the history. */
    val urgePattern: UrgePattern = UrgePattern(),
    /** Where the body is in the current clean stretch. Retires itself after a month. */
    val withdrawal: WithdrawalStatus? = null,
    val now: Instant = Instant.now(),
    val loading: Boolean = true,
)

/** Transient state for the coach conversation: in-flight reply, busy flag and last error. */
data class CoachUiState(
    val busy: Boolean = false,
    val streamingReply: String = "",
    val error: String? = null,
    val verifiedModels: List<String> = emptyList(),
    val verifying: Boolean = false,
    /** The coach's current "do this right now" proposal, shown in the Toolkit. */
    val rescuePlan: String = "",
    val rescueBusy: Boolean = false,
    val insight: String = "",
    val insightBusy: Boolean = false,
)

/** Transient call state. Transcript and reply text are never exposed by the call screen. */
data class VoiceCallUiState(
    val thinking: Boolean = false,
    val replyToSpeak: String = "",
    val replyId: Long = 0L,
    val error: String? = null,
)

sealed interface PaceEvent {
    data class CigaretteLogged(val id: String) : PaceEvent
    data object LogUndone : PaceEvent
    data object PlanSaved : PaceEvent
    data object CheckInSaved : PaceEvent
    data class PauseCompleted(val sessionId: String) : PaceEvent
    data object DataDeleted : PaceEvent
    data object BackupExported : PaceEvent
    data object BackupImported : PaceEvent
    data object BackupFailed : PaceEvent
    data object CoachSettingsSaved : PaceEvent
    data class ApiKeyVerified(val modelCount: Int) : PaceEvent
    data object HistoryUpdated : PaceEvent
    data object HistoryRejected : PaceEvent
}

private data class CoreState(
    val settings: PlanSettings,
    val widget: WidgetSettings,
    val logs: List<CigaretteLog>,
    val sessions: List<UrgeSession>,
    val snapshots: List<com.pace.reduction.domain.model.DailyPlanSnapshot>,
    val now: Instant,
)

private data class AiState(
    val settings: AiSettings,
    val messages: List<CoachMessage>,
)

class PaceViewModel(
    application: PaceApplication,
    private val repository: PaceRepository = application.container.repository,
    private val coachService: CoachService = application.container.coachService,
) : AndroidViewModel(application) {
    init {
        viewModelScope.launch {
            repository.refreshWidgetSnapshot()
        }
        // A camera app may be interrupted before its result callback. Never let that temporary
        // capture survive into a later app session.
        viewModelScope.launch(Dispatchers.IO) { purgeCoachImageCache() }
    }

    private val stepSensor = StepSensor(application)

    private val _coachState = MutableStateFlow(CoachUiState())
    val coachState: StateFlow<CoachUiState> = _coachState.asStateFlow()
    private var coachJob: Job? = null
    private val _voiceCallState = MutableStateFlow(VoiceCallUiState())
    val voiceCallState: StateFlow<VoiceCallUiState> = _voiceCallState.asStateFlow()
    private val voiceHistory = ArrayDeque<OllamaMessage>()
    private var voiceJob: Job? = null

    /**
     * Drives every derived figure on screen, so its rate is the app's foreground cost.
     *
     * Each emission recomputes the full history — progress, pacing and quit metrics — and
     * recomposes Today. Only the Toolkit's five-minute pause is displayed to the second and
     * genuinely needs 1 Hz; everything else is stated in whole minutes, where a second-by-second
     * rebuild is roughly fifty-nine parts waste. So the fast rate is spent only while a pause is
     * actually running.
     *
     * [SharingStarted.WhileSubscribed] already stops this a few seconds after the app is left, so
     * none of it runs in the background either way.
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val ticker: Flow<Instant> = repository.activePause
        .map { it.isRunning }
        .distinctUntilChanged()
        .flatMapLatest { pauseRunning ->
            val periodMs = if (pauseRunning) 1_000L else IDLE_TICK_MS
            flow {
                while (true) {
                    emit(Instant.now())
                    delay(periodMs)
                }
            }
        }

    private val _events = MutableSharedFlow<PaceEvent>(extraBufferCapacity = 8)
    val events: Flow<PaceEvent> = _events

    /** Both halves come off the same preferences store, so pairing them costs nothing and keeps
     *  [coreState] inside `combine`'s five-flow overload. */
    private val preferences = combine(
        repository.settings,
        repository.widgetSettings,
    ) { plan, widget -> plan to widget }

    private val coreState = combine(
        preferences,
        repository.activeLogs,
        repository.urgeSessions,
        repository.dailySnapshots,
        ticker,
    ) { (settings, widget), logs, urgeSessions, snapshots, now ->
        CoreState(settings, widget, logs, urgeSessions, snapshots, now)
    }

    private val aiState = combine(
        repository.aiSettings,
        repository.coachMessages,
    ) { settings, messages -> AiState(settings, messages) }

    val uiState = combine(
        coreState,
        repository.achievements,
        repository.activePause,
        aiState,
        repository.stepDays,
    ) { core, achievements, activePause, ai, stepDays ->
        val zone = ZoneId.systemDefault()
        val progress = ProgressCalculator.calculate(
            today = core.now.atZone(zone).toLocalDate(),
            zoneId = zone,
            logs = core.logs,
            snapshots = core.snapshots,
            sessions = core.sessions,
            pricePerPack = core.settings.pricePerPack,
            cigarettesPerPack = core.settings.cigarettesPerPack,
            rewardTarget = core.settings.rewardTarget,
        )
        // Pacing runs on the adaptive gap so the app and the widget agree on the next window.
        val spacing = AdaptiveSpacing.progress(core.settings, progress.days)
        val today = PacingCalculator.calculate(
            now = core.now,
            zoneId = zone,
            settings = core.settings.copy(minimumGapMinutes = spacing.effectiveMinutes),
            logs = core.logs,
        )
        val quit = QuitProgress.calculate(
            now = core.now,
            zoneId = zone,
            logs = core.logs,
            baselinePerDay = core.settings.baselinePerDay,
            pricePerPack = core.settings.pricePerPack,
            cigarettesPerPack = core.settings.cigarettesPerPack,
            quitDate = core.settings.quitDate,
        )
        val steps = StepCalculator.calculate(
            today = core.now.atZone(zone).toLocalDate(),
            days = stepDays.map { day ->
                val count = day.steps
                StepDay(
                    date = LocalDate.parse(day.localDate),
                    steps = count,
                    distanceKm = StepCalculator.distanceKm(count, core.settings.heightCentimetres),
                )
            },
            available = stepSensor.isAvailable,
            permissionGranted = stepSensor.hasPermission,
            enabled = core.settings.stepCountingEnabled,
        )
        PaceUiState(
            settings = core.settings,
            today = today,
            activeLogs = core.logs,
            dailyCounts = progress.days.takeLast(7).map { DailyCount(it.date, it.count) },
            urgeSessionCount = core.sessions.size,
            urgeSessions = core.sessions,
            progress = progress,
            quit = quit,
            spacing = spacing,
            achievements = achievements,
            activePause = activePause,
            widget = core.widget,
            ai = ai.settings,
            coachMessages = ai.messages,
            steps = steps,
            urgePattern = UrgePatterns.analyse(
                today = core.now.atZone(zone).toLocalDate(),
                zoneId = zone,
                logs = core.logs,
            ),
            withdrawal = WithdrawalTimeline.calculate(quit.smokeFreeDuration),
            now = core.now,
            loading = false,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PaceUiState(),
    )

    /**
     * Takes one reading from the pedometer and folds it into today.
     *
     * Called when the app comes to the foreground and from the periodic worker. The hardware keeps
     * counting regardless, so sampling at those two moments recovers everything walked in between
     * without the app having to stay awake watching.
     */
    fun sampleSteps() {
        viewModelScope.launch {
            val raw = stepSensor.readCounter() ?: return@launch
            repository.recordStepReading(raw)
        }
    }

    /**
     * Turns step counting on or off.
     *
     * Enabling drops the stored anchor first: the counter has been running since boot whether or
     * not the app was watching, and without a fresh baseline the first reading would book every one
     * of those steps as if they had just been taken.
     */
    fun setStepCounting(enabled: Boolean) {
        viewModelScope.launch {
            if (enabled) repository.clearStepAnchor()
            repository.updatePlan(uiState.value.settings.copy(stepCountingEnabled = enabled))
            if (enabled) sampleSteps()
        }
    }

    fun completeOnboarding(plan: PlanSettings) {
        viewModelScope.launch {
            repository.completeOnboarding(plan)
        }
    }

    fun savePlan(plan: PlanSettings) {
        viewModelScope.launch {
            repository.updatePlan(plan)
            _events.emit(PaceEvent.PlanSaved)
        }
    }

    /**
     * Appearance applies immediately and says nothing — a confirmation toast for a colour the user
     * can already see change would only get in the way of trying the next one.
     */
    fun saveAppearance(plan: PlanSettings) {
        viewModelScope.launch { repository.updatePlan(plan) }
    }

    fun saveWidgetSettings(widget: WidgetSettings) {
        viewModelScope.launch { repository.updateWidgetSettings(widget) }
    }

    fun logCigarette() {
        viewModelScope.launch {
            val id = repository.logCigarette()
            _events.emit(PaceEvent.CigaretteLogged(id))
        }
    }

    fun undoLog(id: String) {
        viewModelScope.launch {
            if (repository.undoLog(id)) {
                _events.emit(PaceEvent.LogUndone)
            }
        }
    }

    fun saveUrgeCheckIn(strength: Int?, triggers: Set<String>, note: String) {
        viewModelScope.launch {
            repository.saveUrgeCheckIn(strength, triggers, note)
            _events.emit(PaceEvent.CheckInSaved)
        }
    }

    fun startPause(strength: Int?, triggers: Set<String>, note: String) {
        viewModelScope.launch { repository.startPause(strength, triggers, note) }
    }

    fun pauseTimer() {
        viewModelScope.launch { repository.pauseActivePause() }
    }

    fun resumeTimer() {
        viewModelScope.launch { repository.resumeActivePause() }
    }

    fun cancelTimer() {
        viewModelScope.launch { repository.cancelActivePause() }
    }

    fun completePause() {
        viewModelScope.launch {
            repository.completeActivePause()?.let { _events.emit(PaceEvent.PauseCompleted(it)) }
        }
    }

    fun finishUrgeOutcome(sessionId: String, urgeAfter: Int?, smokedAfter: Boolean?) {
        viewModelScope.launch {
            repository.finishUrgeOutcome(sessionId, urgeAfter, smokedAfter)
            _events.emit(PaceEvent.CheckInSaved)
        }
    }

    fun saveCompletedTool(
        tool: String,
        urgeBefore: Int?,
        urgeAfter: Int?,
        triggers: Set<String>,
        note: String,
        smokedAfter: Boolean?,
        externalRef: String? = null,
    ) {
        viewModelScope.launch {
            repository.saveCompletedTool(tool, urgeBefore, urgeAfter, triggers, note, smokedAfter, externalRef)
            _events.emit(PaceEvent.CheckInSaved)
        }
    }

    /**
     * The row a finished movement session was written to, so an optional after-rating can complete
     * it instead of writing a second session.
     */
    private val _lastMoveSessionId = MutableStateFlow<String?>(null)

    /**
     * Records a finished movement session.
     *
     * The before/after ratings are the point rather than decoration: these are the first tools in
     * the app that can show someone their own number falling, and a session with both ends recorded
     * is the only evidence that the walk did anything.
     */
    fun saveCompletedMove(sessionId: String, urgeBefore: Int?, steps: Long) {
        viewModelScope.launch {
            _lastMoveSessionId.value = repository.saveCompletedTool(
                tool = MOVE_TOOL_PREFIX + sessionId,
                urgeBefore = urgeBefore,
                urgeAfter = null,
                triggerTags = emptySet(),
                note = if (steps > 0) "steps=$steps" else "",
                smokedAfter = null,
            )
            _events.emit(PaceEvent.CheckInSaved)
        }
    }

    /** Fills in how the last movement session ended. A no-op if nothing was recorded to fill in. */
    fun rateLastMove(urgeAfter: Int) {
        val sessionId = _lastMoveSessionId.value ?: return
        viewModelScope.launch { repository.finishUrgeOutcome(sessionId, urgeAfter, null) }
    }

    fun sendCoachMessage(text: String, imageBytes: ByteArray? = null) {
        val prompt = text.trim().ifEmpty {
            if (imageBytes != null) "What do you notice in this photo?" else return
        }
        if (_coachState.value.busy) return
        coachJob?.cancel()
        coachJob = viewModelScope.launch {
            val image = if (imageBytes == null) {
                null
            } else {
                runCatching { withContext(Dispatchers.Default) { prepareVisionImage(imageBytes) } }
                    .getOrElse { error ->
                        _coachState.update { it.copy(error = error.userMessage()) }
                        return@launch
                    }
            }
            // The prompt stays in chat history. Photo bytes go straight to Ollama and are not
            // persisted on-device by Pace.
            val messageId = repository.appendCoachMessage("user", if (image == null) prompt else "📷 $prompt")
            if (image == null) {
                streamReply { coachService.chatStream() }
            } else {
                completeImageReply(messageId, prompt, image)
            }
        }
    }

    private suspend fun completeImageReply(messageId: String, prompt: String, imageBase64: String) {
        _coachState.update { it.copy(busy = true, error = null, streamingReply = "") }
        runCatching { coachService.imageReply(imageBase64) }.fold(
            onSuccess = { result ->
                repository.updateCoachMessage(
                    messageId,
                    CoachImageMemory.encode(prompt, result.imageContext),
                )
                if (result.reply.isNotBlank()) repository.appendCoachMessage("assistant", result.reply)
                _coachState.update { it.copy(busy = false, streamingReply = "") }
            },
            onFailure = { error ->
                if (error is CancellationException) throw error
                _coachState.update {
                    it.copy(busy = false, streamingReply = "", error = error.userMessage())
                }
            },
        )
    }

    /** Sends one speech transcript while keeping the spoken conversation only in memory. */
    fun sendVoiceMessage(text: String) {
        val prompt = text.trim().take(500)
        if (prompt.isEmpty() || _voiceCallState.value.thinking) return
        voiceJob?.cancel()
        voiceJob = viewModelScope.launch {
            _voiceCallState.value = VoiceCallUiState(thinking = true)
            voiceHistory.addLast(OllamaMessage("user", prompt))
            while (voiceHistory.size > VOICE_HISTORY_LIMIT) voiceHistory.removeFirst()
            runCatching { coachService.voiceReply(voiceHistory.toList()) }.fold(
                onSuccess = { reply ->
                    if (reply.isBlank()) {
                        _voiceCallState.value = VoiceCallUiState(
                            error = "The coach did not return a spoken reply",
                        )
                    } else {
                        voiceHistory.addLast(OllamaMessage("assistant", reply))
                        while (voiceHistory.size > VOICE_HISTORY_LIMIT) voiceHistory.removeFirst()
                        _voiceCallState.value = VoiceCallUiState(
                            replyToSpeak = reply,
                            replyId = System.nanoTime(),
                        )
                    }
                },
                onFailure = { error ->
                    if (error is CancellationException) throw error
                    if (voiceHistory.lastOrNull()?.role == "user") voiceHistory.removeLast()
                    _voiceCallState.value = VoiceCallUiState(error = error.userMessage())
                },
            )
        }
    }

    fun consumeVoiceReply() = _voiceCallState.update { it.copy(replyToSpeak = "") }

    fun dismissVoiceError() = _voiceCallState.update { it.copy(error = null) }

    fun stopVoiceCall() {
        voiceJob?.cancel()
        voiceJob = null
        voiceHistory.clear()
        _voiceCallState.value = VoiceCallUiState()
    }

    /** Asks the coach for a riddle without the user having to type anything. */
    fun requestRiddle() {
        if (_coachState.value.busy) return
        coachJob?.cancel()
        coachJob = viewModelScope.launch {
            _coachState.update { it.copy(busy = true, error = null, streamingReply = "") }
            val result = runCatching { coachService.riddle() }
            result.fold(
                onSuccess = { reply ->
                    if (reply.isNotBlank()) repository.appendCoachMessage("assistant", reply)
                    _coachState.update { it.copy(busy = false, streamingReply = "") }
                },
                onFailure = { error ->
                    _coachState.update { it.copy(busy = false, streamingReply = "", error = error.userMessage()) }
                },
            )
        }
    }

    /** Asks the coach for one concrete thing to do right now, shown in the Toolkit. */
    fun requestRescuePlan() {
        if (_coachState.value.rescueBusy) return
        viewModelScope.launch {
            _coachState.update { it.copy(rescueBusy = true, error = null) }
            runCatching { coachService.rescuePlan() }.fold(
                onSuccess = { plan ->
                    _coachState.update { it.copy(rescueBusy = false, rescuePlan = plan.ifBlank { it.rescuePlan }) }
                },
                onFailure = { error ->
                    _coachState.update { it.copy(rescueBusy = false, error = error.userMessage()) }
                },
            )
        }
    }

    fun clearRescuePlan() = _coachState.update { it.copy(rescuePlan = "") }

    /** Refreshes the "what Pace notices" line on Progress. */
    fun requestInsight() {
        if (_coachState.value.insightBusy) return
        viewModelScope.launch {
            _coachState.update { it.copy(insightBusy = true, error = null) }
            runCatching { coachService.insight() }.fold(
                onSuccess = { text ->
                    _coachState.update { it.copy(insightBusy = false, insight = text.ifBlank { it.insight }) }
                },
                onFailure = { error ->
                    _coachState.update { it.copy(insightBusy = false, error = error.userMessage()) }
                },
            )
        }
    }

    fun stopCoach() {
        coachJob?.cancel()
        coachJob = null
        val partial = _coachState.value.streamingReply
        _coachState.update { it.copy(busy = false, streamingReply = "") }
        if (partial.isNotBlank()) {
            viewModelScope.launch { repository.appendCoachMessage("assistant", partial) }
        }
    }

    fun clearCoach() {
        coachJob?.cancel()
        coachJob = null
        _coachState.value = CoachUiState()
        viewModelScope.launch {
            repository.clearCoachMessages()
            withContext(Dispatchers.IO) { purgeCoachImageCache() }
        }
    }

    fun dismissCoachError() = _coachState.update { it.copy(error = null) }

    /** Checks the pasted key against Ollama Cloud and caches the models it can reach. */
    fun verifyApiKey(apiKey: String) {
        if (_coachState.value.verifying) return
        viewModelScope.launch {
            _coachState.update { it.copy(verifying = true, error = null) }
            runCatching { coachService.verify(apiKey) }.fold(
                onSuccess = { models ->
                    _coachState.update { it.copy(verifying = false, verifiedModels = models) }
                    _events.emit(PaceEvent.ApiKeyVerified(models.size))
                },
                onFailure = { error ->
                    _coachState.update { it.copy(verifying = false, error = error.userMessage()) }
                },
            )
        }
    }

    fun saveAiSettings(
        enabled: Boolean,
        apiKey: String,
        model: String,
        visionModel: String,
        proactiveNudges: Boolean,
    ) {
        viewModelScope.launch {
            repository.saveAiSettings(enabled, apiKey, model, visionModel, proactiveNudges)
            _events.emit(PaceEvent.CoachSettingsSaved)
        }
    }

    /** Logs for the day being edited in the history screen. */
    private val _editorDate = MutableStateFlow(LocalDate.now())
    val editorDate: StateFlow<LocalDate> = _editorDate.asStateFlow()

    private val _editorLogs = MutableStateFlow<List<CigaretteLog>>(emptyList())
    val editorLogs: StateFlow<List<CigaretteLog>> = _editorLogs.asStateFlow()

    fun selectEditorDate(date: LocalDate) {
        if (date.isAfter(LocalDate.now())) return
        _editorDate.value = date
        refreshEditorLogs()
    }

    fun refreshEditorLogs() {
        viewModelScope.launch {
            _editorLogs.value = repository.logsOn(_editorDate.value)
        }
    }

    fun addHistoryEntry(hour: Int, minute: Int) {
        viewModelScope.launch {
            val moment = _editorDate.value.atTime(hour.coerceIn(0, 23), minute.coerceIn(0, 59))
            val result = runCatching { repository.addLogAt(moment) }
            if (result.isSuccess) {
                _editorLogs.value = repository.logsOn(_editorDate.value)
                _events.emit(PaceEvent.HistoryUpdated)
            } else {
                _events.emit(PaceEvent.HistoryRejected)
            }
        }
    }

    fun deleteHistoryEntry(id: String) {
        viewModelScope.launch {
            repository.deleteLog(id)
            _editorLogs.value = repository.logsOn(_editorDate.value)
            _events.emit(PaceEvent.HistoryUpdated)
        }
    }

    fun saveCoachBehaviour(
        systemPrompt: String,
        imageSystemPrompt: String,
        includeStats: Boolean,
        checkupsEnabled: Boolean,
        checkupIntervalMinutes: Int,
    ) {
        viewModelScope.launch {
            repository.saveCoachBehaviour(
                systemPrompt,
                imageSystemPrompt,
                includeStats,
                checkupsEnabled,
                checkupIntervalMinutes,
            )
            _events.emit(PaceEvent.CoachSettingsSaved)
        }
    }

    fun clearApiKey() {
        viewModelScope.launch {
            repository.clearApiKey()
            _coachState.value = CoachUiState()
        }
    }

    private suspend fun streamReply(source: suspend () -> Flow<String>) {
        _coachState.update { it.copy(busy = true, error = null, streamingReply = "") }
        val builder = StringBuilder()
        runCatching {
            source().collect { token ->
                builder.append(token)
                _coachState.update { it.copy(streamingReply = builder.toString()) }
            }
        }.fold(
            onSuccess = {
                val reply = builder.toString().trim()
                if (reply.isNotEmpty()) repository.appendCoachMessage("assistant", reply)
                _coachState.update { it.copy(busy = false, streamingReply = "") }
            },
            onFailure = { error ->
                if (error is CancellationException) throw error
                val partial = builder.toString().trim()
                if (partial.isNotEmpty()) repository.appendCoachMessage("assistant", partial)
                _coachState.update { it.copy(busy = false, streamingReply = "", error = error.userMessage()) }
            },
        )
    }

    private fun Throwable.userMessage(): String =
        message?.takeIf { it.isNotBlank() }?.take(160) ?: "The coach could not be reached"

    fun deleteAllData() {
        viewModelScope.launch {
            repository.deleteAllData()
            withContext(Dispatchers.IO) { purgeCoachImageCache() }
            _events.emit(PaceEvent.DataDeleted)
        }
    }

    fun exportData(uri: Uri) {
        viewModelScope.launch {
            val result = runCatching {
                val json = repository.exportJson()
                withContext(Dispatchers.IO) {
                    getApplication<PaceApplication>().contentResolver.openOutputStream(uri, "wt")
                        ?.bufferedWriter()
                        ?.use { it.write(json) }
                        ?: error("Cannot open export destination")
                }
            }
            _events.emit(if (result.isSuccess) PaceEvent.BackupExported else PaceEvent.BackupFailed)
        }
    }

    fun importData(uri: Uri) {
        viewModelScope.launch {
            val result = runCatching {
                val json = withContext(Dispatchers.IO) {
                    getApplication<PaceApplication>().contentResolver.openInputStream(uri)
                        ?.bufferedReader()
                        ?.use { reader ->
                            val buffer = CharArray(8_192)
                            val output = StringBuilder()
                            while (true) {
                                val read = reader.read(buffer)
                                if (read < 0) break
                                output.append(buffer, 0, read)
                                require(output.length <= 1_000_000) { "Backup is too large" }
                            }
                            output.toString()
                        }
                        ?: error("Cannot open import source")
                }
                repository.importJson(json)
            }
            _events.emit(if (result.isSuccess) PaceEvent.BackupImported else PaceEvent.BackupFailed)
        }
    }

    /** Downsamples and normalises a picked image before base64 inflates it for the REST request. */
    private fun prepareVisionImage(input: ByteArray): String {
        require(input.isNotEmpty()) { "That photo could not be read" }
        require(input.size <= MAX_IMAGE_INPUT_BYTES) { "Choose a photo smaller than 20 MB" }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(input, 0, input.size, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "That file is not a supported photo" }

        var sample = 1
        while (bounds.outWidth / sample > MAX_IMAGE_EDGE * 2 || bounds.outHeight / sample > MAX_IMAGE_EDGE * 2) {
            sample *= 2
        }
        val decoded = BitmapFactory.decodeByteArray(
            input,
            0,
            input.size,
            BitmapFactory.Options().apply { inSampleSize = sample },
        ) ?: error("That photo could not be decoded")
        val largest = maxOf(decoded.width, decoded.height)
        val scaled = if (largest > MAX_IMAGE_EDGE) {
            val ratio = MAX_IMAGE_EDGE.toFloat() / largest
            android.graphics.Bitmap.createScaledBitmap(
                decoded,
                (decoded.width * ratio).toInt().coerceAtLeast(1),
                (decoded.height * ratio).toInt().coerceAtLeast(1),
                true,
            ).also { decoded.recycle() }
        } else {
            decoded
        }
        return try {
            val output = ByteArrayOutputStream()
            require(scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, 88, output)) {
                "That photo could not be prepared"
            }
            Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
        } finally {
            scaled.recycle()
        }
    }

    /** Captures live only in this private cache directory and are never part of chat storage. */
    private fun purgeCoachImageCache() {
        val directory = File(getApplication<PaceApplication>().cacheDir, COACH_IMAGE_CACHE_DIRECTORY)
        directory.listFiles()?.forEach { file -> if (file.isFile) file.delete() }
        directory.delete()
    }

    companion object {
        /**
         * Fast enough that a window opening feels immediate and the "1h 36m" line never looks
         * wrong, slow enough that sitting on Today is not a busy loop.
         */
        private const val IDLE_TICK_MS = 5_000L
        private const val VOICE_HISTORY_LIMIT = 20
        private const val MAX_IMAGE_INPUT_BYTES = 20 * 1024 * 1024
        private const val MAX_IMAGE_EDGE = 1_536
        private const val COACH_IMAGE_CACHE_DIRECTORY = "coach-images"

        fun factory(application: PaceApplication): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    PaceViewModel(application) as T
            }
    }
}
