package com.pace.reduction

import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import android.net.Uri
import com.pace.reduction.core.coach.CoachService
import com.pace.reduction.data.repository.PaceRepository
import com.pace.reduction.domain.PacingCalculator
import com.pace.reduction.domain.ProgressCalculator
import com.pace.reduction.domain.ProgressMetrics
import com.pace.reduction.domain.AdaptiveSpacing
import com.pace.reduction.domain.QuitMetrics
import com.pace.reduction.domain.QuitProgress
import com.pace.reduction.domain.SpacingProgress
import com.pace.reduction.domain.model.ActivePause
import com.pace.reduction.domain.model.Achievement
import com.pace.reduction.domain.model.AiSettings
import com.pace.reduction.domain.model.CigaretteLog
import com.pace.reduction.domain.model.CoachMessage
import com.pace.reduction.domain.model.DailyCount
import com.pace.reduction.domain.model.PlanSettings
import com.pace.reduction.domain.model.TodaySummary
import com.pace.reduction.domain.model.TriggerPlace
import com.pace.reduction.domain.model.UrgeSession
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
import kotlinx.coroutines.flow.flow
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
    val triggerPlaces: List<TriggerPlace> = emptyList(),
    val activePause: ActivePause = ActivePause(),
    val ai: AiSettings = AiSettings(),
    val coachMessages: List<CoachMessage> = emptyList(),
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
}

private data class CoreState(
    val settings: PlanSettings,
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
    }

    private val _coachState = MutableStateFlow(CoachUiState())
    val coachState: StateFlow<CoachUiState> = _coachState.asStateFlow()
    private var coachJob: Job? = null

    private val ticker: Flow<Instant> = flow {
        while (true) {
            emit(Instant.now())
            delay(1_000)
        }
    }

    private val _events = MutableSharedFlow<PaceEvent>(extraBufferCapacity = 8)
    val events: Flow<PaceEvent> = _events

    private val coreState = combine(
        repository.settings,
        repository.activeLogs,
        repository.urgeSessions,
        repository.dailySnapshots,
        ticker,
    ) { settings, logs, urgeSessions, snapshots, now ->
        CoreState(settings, logs, urgeSessions, snapshots, now)
    }

    private val aiState = combine(
        repository.aiSettings,
        repository.coachMessages,
    ) { settings, messages -> AiState(settings, messages) }

    val uiState = combine(
        coreState,
        repository.achievements,
        repository.triggerPlaces,
        repository.activePause,
        aiState,
    ) { core, achievements, triggerPlaces, activePause, ai ->
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
            triggerPlaces = triggerPlaces,
            activePause = activePause,
            ai = ai.settings,
            coachMessages = ai.messages,
            now = core.now,
            loading = false,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PaceUiState(),
    )

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

    fun saveTriggerPlace(
        label: String,
        latitude: Double,
        longitude: Double,
        radiusMeters: Int,
    ) {
        viewModelScope.launch { repository.saveTriggerPlace(label = label, latitude = latitude, longitude = longitude, radiusMeters = radiusMeters) }
    }

    fun deleteTriggerPlace(id: String) {
        viewModelScope.launch { repository.deleteTriggerPlace(id) }
    }

    fun updateTriggerPlace(id: String, label: String, enabled: Boolean, automaticCueEnabled: Boolean) {
        viewModelScope.launch { repository.updateTriggerPlace(id, label, enabled, automaticCueEnabled) }
    }

    fun sendCoachMessage(text: String) {
        val prompt = text.trim()
        if (prompt.isEmpty() || _coachState.value.busy) return
        coachJob?.cancel()
        coachJob = viewModelScope.launch {
            // Persist first so the service replays this turn along with the prior conversation.
            repository.appendCoachMessage("user", prompt)
            streamReply { coachService.chatStream() }
        }
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
        viewModelScope.launch { repository.clearCoachMessages() }
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

    fun saveAiSettings(enabled: Boolean, apiKey: String, model: String, proactiveNudges: Boolean) {
        viewModelScope.launch {
            repository.saveAiSettings(enabled, apiKey, model, proactiveNudges)
            _events.emit(PaceEvent.CoachSettingsSaved)
        }
    }

    fun saveCoachBehaviour(
        systemPrompt: String,
        includeStats: Boolean,
        checkupsEnabled: Boolean,
        checkupIntervalMinutes: Int,
    ) {
        viewModelScope.launch {
            repository.saveCoachBehaviour(systemPrompt, includeStats, checkupsEnabled, checkupIntervalMinutes)
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

    private fun lastSevenDays(now: Instant, zone: ZoneId, logs: List<CigaretteLog>): List<DailyCount> {
        val today = now.atZone(zone).toLocalDate()
        val grouped = logs
            .filter { it.reversedAt == null }
            .groupingBy { it.occurredAt.atZone(zone).toLocalDate() }
            .eachCount()
        return (6 downTo 0).map { offset ->
            val date = today.minusDays(offset.toLong())
            DailyCount(date, grouped[date] ?: 0)
        }
    }

    companion object {
        fun factory(application: PaceApplication): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    PaceViewModel(application) as T
            }
    }
}
