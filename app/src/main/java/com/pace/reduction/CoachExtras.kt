package com.pace.reduction

import com.pace.reduction.core.coach.CoachService
import com.pace.reduction.data.repository.PaceRepository
import com.pace.reduction.domain.ArcadeOffline
import com.pace.reduction.domain.Autopilot
import com.pace.reduction.domain.AutopilotPick
import com.pace.reduction.domain.EmojiPuzzle
import com.pace.reduction.domain.IfThenPlan
import com.pace.reduction.domain.Playbook
import com.pace.reduction.domain.StoryBeat
import com.pace.reduction.domain.ToolInsights
import com.pace.reduction.domain.TriviaQuestion
import com.pace.reduction.domain.model.UrgeSession
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A round of trivia or emoji puzzles, and whether it came from the model or the offline bank. */
data class ArcadeRound<T>(
    val items: List<T> = emptyList(),
    val offline: Boolean = false,
    /** Changes for every new round, so the UI can reset its per-round state. */
    val roundId: Long = 0L,
)

data class StoryState(
    val genre: String = "",
    /** Every scene so far, newest last. */
    val beats: List<StoryBeat> = emptyList(),
    /** The choice made after each beat except the newest. */
    val choices: List<String> = emptyList(),
) {
    val current: StoryBeat? get() = beats.lastOrNull()
    val started: Boolean get() = beats.isNotEmpty()
}

data class ArcadeUiState(
    val busy: Boolean = false,
    val error: String? = null,
    val trivia: ArcadeRound<TriviaQuestion> = ArcadeRound(),
    val emoji: ArcadeRound<EmojiPuzzle> = ArcadeRound(),
    val story: StoryState = StoryState(),
)

data class AutopilotUiState(
    val busy: Boolean = false,
    val pick: AutopilotPick? = null,
    /** The strength reported before the pick, kept so the tool can be filed with a before rating. */
    val strength: Int? = null,
    /** Set once a picked tool finishes, so an optional after-rating can complete it. */
    val awaitingRating: Boolean = false,
)

data class PlaybookUiState(
    val busy: Boolean = false,
    val suggestions: List<IfThenPlan> = emptyList(),
    val error: String? = null,
)

/**
 * The model-driven corners of the app that are not the chat: the arcade, the "pick for me" agent,
 * and the playbook drafter.
 *
 * Kept out of [PaceViewModel] so the view model stays about the day's figures. Everything here is
 * transient — a game round or a suggestion that has not been kept is not worth writing to disk.
 */
class CoachExtras(
    private val scope: CoroutineScope,
    private val repository: PaceRepository,
    private val coachService: CoachService,
    private val clock: () -> Instant = Instant::now,
) {
    private val _arcade = MutableStateFlow(ArcadeUiState())
    val arcade: StateFlow<ArcadeUiState> = _arcade.asStateFlow()

    private val _autopilot = MutableStateFlow(AutopilotUiState())
    val autopilot: StateFlow<AutopilotUiState> = _autopilot.asStateFlow()

    private val _playbook = MutableStateFlow(PlaybookUiState())
    val playbook: StateFlow<PlaybookUiState> = _playbook.asStateFlow()

    private var arcadeJob: Job? = null

    /** A fresh trivia round from the model, or the offline bank when the coach is off or unreachable. */
    fun newTrivia(topic: String, coachReady: Boolean) = launchArcade { seed ->
        val fromModel = if (coachReady) runModel { coachService.triviaRound(topic) } else null
        _arcade.update {
            it.copy(
                busy = false,
                // The offline label on the round already says why; a red error on top is noise.
                error = null,
                trivia = ArcadeRound(
                    items = fromModel ?: ArcadeOffline.triviaRound(seed),
                    offline = fromModel == null,
                    roundId = seed,
                ),
            )
        }
    }

    fun newEmojiRound(coachReady: Boolean) = launchArcade { seed ->
        val fromModel = if (coachReady) runModel { coachService.emojiRound() } else null
        _arcade.update {
            it.copy(
                busy = false,
                // The offline label on the round already says why; a red error on top is noise.
                error = null,
                emoji = ArcadeRound(
                    items = fromModel ?: ArcadeOffline.emojiRound(seed),
                    offline = fromModel == null,
                    roundId = seed,
                ),
            )
        }
    }

    fun startStory(genre: String) = launchArcade {
        _arcade.update { it.copy(story = StoryState(genre = genre)) }
        val beat = runModel { coachService.storyBeat(genre, emptyList()) }
        _arcade.update { state ->
            state.copy(
                busy = false,
                story = if (beat == null) StoryState() else StoryState(genre = genre, beats = listOf(beat)),
            )
        }
    }

    fun chooseInStory(choice: String) {
        val story = _arcade.value.story
        val current = story.current ?: return
        if (current.ending || choice !in current.choices) return
        launchArcade {
            val path = story.beats.mapIndexed { index, beat ->
                beat.scene to (story.choices.getOrNull(index) ?: choice)
            }
            val next = runModel { coachService.storyBeat(story.genre, path) }
            _arcade.update { state ->
                if (next == null) {
                    state.copy(busy = false)
                } else {
                    state.copy(
                        busy = false,
                        story = story.copy(beats = story.beats + next, choices = story.choices + choice),
                    )
                }
            }
        }
    }

    fun resetStory() {
        arcadeJob?.cancel()
        _arcade.update { it.copy(busy = false, story = StoryState(), error = null) }
    }

    fun dismissArcadeError() = _arcade.update { it.copy(error = null) }

    /** Closing a game drops its round, so reopening deals a new one rather than replaying the old. */
    fun clearTrivia() {
        arcadeJob?.cancel()
        _arcade.update { it.copy(busy = false, error = null, trivia = ArcadeRound()) }
    }

    fun clearEmoji() {
        arcadeJob?.cancel()
        _arcade.update { it.copy(busy = false, error = null, emoji = ArcadeRound()) }
    }

    /**
     * Asks the agent to choose a tool. The device picker answers instantly when the coach is off,
     * and stands in when the model fails or names something the app cannot open.
     */
    fun pickForMe(strength: Int?, sessions: List<UrgeSession>, coachReady: Boolean) {
        if (_autopilot.value.busy) return
        scope.launch {
            _autopilot.value = AutopilotUiState(busy = true, strength = strength)
            val now = clock()
            val stats = ToolInsights.calculate(sessions, now)
            val recent = ToolInsights.recent(sessions)
            val fromModel = if (coachReady) {
                try {
                    coachService.autopilot(strength, recent, stats)
                } catch (error: CancellationException) {
                    throw error
                } catch (failure: Exception) {
                    // The device picker below is the answer; the failure only changes who chose.
                    null
                }
            } else {
                null
            }
            val zoned = now.atZone(ZoneId.systemDefault())
            val pick = fromModel ?: Autopilot.fallback(
                strength = strength,
                minuteOfDay = zoned.hour * 60 + zoned.minute,
                stats = stats,
                recent = recent,
                coachReady = coachReady,
                seed = now.toEpochMilli(),
            )
            _autopilot.value = AutopilotUiState(pick = pick, strength = strength)
        }
    }

    /** The picked tool was opened and finished; offer the after-rating if a before was given. */
    fun autopilotToolFinished() = _autopilot.update {
        if (it.pick != null && it.strength != null) it.copy(awaitingRating = true) else AutopilotUiState()
    }

    fun clearAutopilot() = _autopilot.update { AutopilotUiState() }

    /** Asks the coach for three plans tailored to their day, leaving out the ones already kept. */
    fun draftPlaybook() {
        if (_playbook.value.busy) return
        scope.launch {
            _playbook.update { it.copy(busy = true, error = null) }
            val existing = repository.settings.first().ifThenPlans
            try {
                val drafts = coachService.draftPlaybook(existing)
                _playbook.update { it.copy(busy = false, suggestions = drafts) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _playbook.update { it.copy(busy = false, error = error.userMessage()) }
            }
        }
    }

    fun keepSuggestion(plan: IfThenPlan) {
        scope.launch {
            val existing = repository.settings.first().ifThenPlans
            repository.savePlaybook(existing + plan)
            _playbook.update { state -> state.copy(suggestions = state.suggestions - plan) }
        }
    }

    fun dismissSuggestion(plan: IfThenPlan) =
        _playbook.update { state -> state.copy(suggestions = state.suggestions - plan) }

    fun savePlans(plans: List<IfThenPlan>) {
        scope.launch { repository.savePlaybook(Playbook.sanitise(plans)) }
    }

    fun dismissPlaybookError() = _playbook.update { it.copy(error = null) }

    /** Runs one arcade request at a time; a new round replaces whatever was still loading. */
    private fun launchArcade(block: suspend (seed: Long) -> Unit) {
        arcadeJob?.cancel()
        arcadeJob = scope.launch {
            _arcade.update { it.copy(busy = true, error = null) }
            block(clock().toEpochMilli())
        }
    }

    /** A model call whose failure becomes an on-screen error instead of a crash. */
    private suspend fun <T> runModel(call: suspend () -> T): T? = try {
        call()
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        _arcade.update { it.copy(error = error.userMessage()) }
        null
    }

    private fun Throwable.userMessage(): String =
        message?.takeIf { it.isNotBlank() }?.take(160) ?: "The coach could not be reached"
}
