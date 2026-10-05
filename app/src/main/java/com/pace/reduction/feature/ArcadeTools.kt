package com.pace.reduction.feature

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.Quiz
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pace.reduction.ArcadeUiState
import com.pace.reduction.R
import com.pace.reduction.core.designsystem.LocalMotion
import com.pace.reduction.domain.AnswerMatcher
import com.pace.reduction.domain.ArcadeOffline
import com.pace.reduction.domain.ToolDirectory

/** The arcade's three games, as shelf rows. Ids match [ToolDirectory] so sessions file correctly. */
internal val arcadeEntries = listOf(
    ToolEntry(ToolDirectory.STORY, R.string.story_title, R.string.story_summary, 4, Icons.Outlined.AutoStories),
    ToolEntry(ToolDirectory.TRIVIA, R.string.trivia_title, R.string.trivia_summary, 3, Icons.Outlined.Quiz),
    ToolEntry(ToolDirectory.EMOJI, R.string.emoji_title, R.string.emoji_summary, 3, Icons.Outlined.EmojiEmotions),
)

/**
 * Trivia sprint: five questions, written fresh by the model on a topic of the user's choosing.
 *
 * A wrong answer still pays out — each question carries a one-line fact that is revealed either
 * way, because the point is three minutes of curiosity rather than a score to feel bad about.
 */
@Composable
internal fun TriviaTool(
    state: ArcadeUiState,
    coachReady: Boolean,
    hapticsEnabled: Boolean,
    onNewRound: (topic: String) -> Unit,
    onRoundComplete: () -> Unit,
    onClose: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val motion = LocalMotion.current
    val round = state.trivia
    var topic by rememberSaveable { mutableStateOf(ArcadeOffline.triviaTopics.first()) }
    var index by rememberSaveable(round.roundId) { mutableIntStateOf(0) }
    var chosen by rememberSaveable(round.roundId, index) { mutableStateOf<Int?>(null) }
    var score by rememberSaveable(round.roundId) { mutableIntStateOf(0) }
    val finished = round.items.isNotEmpty() && index >= round.items.size
    // Saved, so a rotation on the score screen does not file the same round twice.
    var recorded by rememberSaveable(round.roundId) { mutableStateOf(false) }

    LaunchedEffect(round.roundId, finished) {
        if (finished && !recorded) {
            recorded = true
            onRoundComplete()
        }
    }

    ToolShell(
        title = stringResource(R.string.trivia_title),
        evidence = stringResource(R.string.arcade_evidence),
        onClose = onClose,
    ) {
        when {
            state.busy && round.items.isEmpty() -> ArcadeLoading(stringResource(R.string.trivia_loading))

            round.items.isEmpty() -> {
                Text(stringResource(R.string.trivia_pick_topic), style = MaterialTheme.typography.titleSmall)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    ArcadeOffline.triviaTopics.forEach { option ->
                        FilterChip(
                            selected = topic == option,
                            onClick = { topic = option },
                            label = { Text(option) },
                        )
                    }
                }
                if (!coachReady) OfflineNote(stringResource(R.string.arcade_offline_bank))
                Button(onClick = { onNewRound(topic) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.arcade_start))
                }
            }

            finished -> ArcadeResult(
                score = score,
                total = round.items.size,
                offline = round.offline,
                busy = state.busy,
                onAgain = { onNewRound(topic) },
                onClose = onClose,
            )

            else -> {
                val question = round.items[index]
                RoundProgress(index = index, total = round.items.size, offline = round.offline)
                AnimatedContent(
                    targetState = index,
                    transitionSpec = { fadeIn(motion.eased(200)) togetherWith fadeOut(motion.eased(160)) },
                    label = "triviaQuestion",
                ) { shown ->
                    Text(
                        round.items.getOrNull(shown)?.question ?: question.question,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                question.options.forEachIndexed { optionIndex, option ->
                    val answered = chosen != null
                    val correct = optionIndex == question.answerIndex
                    val picked = chosen == optionIndex
                    val colours = when {
                        answered && correct -> ButtonDefaults.outlinedButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        answered && picked -> ButtonDefaults.outlinedButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        else -> ButtonDefaults.outlinedButtonColors()
                    }
                    OutlinedButton(
                        onClick = {
                            if (!answered) {
                                chosen = optionIndex
                                if (correct) score += 1
                                if (hapticsEnabled) {
                                    haptics.performHapticFeedback(
                                        if (correct) HapticFeedbackType.LongPress else HapticFeedbackType.TextHandleMove,
                                    )
                                }
                            }
                        },
                        colors = colours,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (answered && (correct || picked)) {
                            Icon(
                                if (correct) Icons.Outlined.CheckCircle else Icons.Outlined.Cancel,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.size(8.dp))
                        }
                        Text(option, modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
                    }
                }
                if (chosen != null) {
                    if (question.fact.isNotBlank()) {
                        Text(
                            question.fact,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Button(onClick = { index += 1 }, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            stringResource(
                                if (index + 1 >= round.items.size) R.string.arcade_see_score else R.string.arcade_next,
                            ),
                        )
                    }
                }
            }
        }
        state.error?.let { ArcadeError(it) }
    }
}

/**
 * Emoji decoder: a few emoji spell out a film, a saying or a thing.
 *
 * Typed rather than multiple choice on purpose — producing an answer occupies more of the head than
 * recognising one, which is the whole job here. Matching is forgiving about articles, case and a
 * typo, so the challenge is the puzzle and not the keyboard.
 */
@Composable
internal fun EmojiTool(
    state: ArcadeUiState,
    coachReady: Boolean,
    hapticsEnabled: Boolean,
    onNewRound: () -> Unit,
    onRoundComplete: () -> Unit,
    onClose: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val round = state.emoji
    var index by rememberSaveable(round.roundId) { mutableIntStateOf(0) }
    var guess by rememberSaveable(round.roundId, index) { mutableStateOf("") }
    var hintShown by rememberSaveable(round.roundId, index) { mutableStateOf(false) }
    var revealed by rememberSaveable(round.roundId, index) { mutableStateOf(false) }
    var solved by rememberSaveable(round.roundId, index) { mutableStateOf(false) }
    var wrong by rememberSaveable(round.roundId, index) { mutableStateOf(false) }
    var score by rememberSaveable(round.roundId) { mutableIntStateOf(0) }
    val finished = round.items.isNotEmpty() && index >= round.items.size
    var recorded by rememberSaveable(round.roundId) { mutableStateOf(false) }

    LaunchedEffect(round.roundId, finished) {
        if (finished && !recorded) {
            recorded = true
            onRoundComplete()
        }
    }

    ToolShell(
        title = stringResource(R.string.emoji_title),
        evidence = stringResource(R.string.arcade_evidence),
        onClose = onClose,
    ) {
        when {
            state.busy && round.items.isEmpty() -> ArcadeLoading(stringResource(R.string.emoji_loading))

            round.items.isEmpty() -> {
                Text(stringResource(R.string.emoji_intro), style = MaterialTheme.typography.bodyMedium)
                if (!coachReady) OfflineNote(stringResource(R.string.arcade_offline_bank))
                Button(onClick = onNewRound, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.arcade_start))
                }
            }

            finished -> ArcadeResult(
                score = score,
                total = round.items.size,
                offline = round.offline,
                busy = state.busy,
                onAgain = onNewRound,
                onClose = onClose,
            )

            else -> {
                val puzzle = round.items[index]
                val done = solved || revealed
                RoundProgress(index = index, total = round.items.size, offline = round.offline)
                if (puzzle.category.isNotBlank()) {
                    Text(
                        puzzle.category,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    puzzle.emojis,
                    fontSize = 44.sp,
                    lineHeight = 52.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                )
                if (hintShown && puzzle.hint.isNotBlank() && !done) {
                    Text(
                        stringResource(R.string.emoji_hint_value, puzzle.hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (done) {
                    Surface(
                        color = if (solved) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            stringResource(
                                if (solved) R.string.emoji_correct else R.string.emoji_answer_was,
                                puzzle.answer,
                            ),
                            modifier = Modifier.padding(14.dp),
                            style = MaterialTheme.typography.titleSmall,
                        )
                    }
                    Button(onClick = { index += 1 }, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            stringResource(
                                if (index + 1 >= round.items.size) R.string.arcade_see_score else R.string.arcade_next,
                            ),
                        )
                    }
                } else {
                    val check = {
                        if (guess.isNotBlank()) {
                            if (AnswerMatcher.matches(guess, puzzle.answer)) {
                                solved = true
                                score += 1
                                if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            } else {
                                wrong = true
                                if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        }
                    }
                    OutlinedTextField(
                        value = guess,
                        onValueChange = {
                            guess = it.take(60)
                            wrong = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.emoji_guess_label)) },
                        isError = wrong,
                        supportingText = {
                            if (wrong) Text(stringResource(R.string.emoji_not_quite))
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { check() }),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = check, enabled = guess.isNotBlank(), modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.emoji_check))
                        }
                        if (!hintShown && puzzle.hint.isNotBlank()) {
                            OutlinedButton(onClick = { hintShown = true }) {
                                Text(stringResource(R.string.emoji_hint))
                            }
                        }
                        TextButton(onClick = { revealed = true }) {
                            Text(stringResource(R.string.emoji_reveal))
                        }
                    }
                }
            }
        }
        state.error?.let { ArcadeError(it) }
    }
}

/**
 * Story quest: a five-beat story the user steers with a tap.
 *
 * The one arcade game with no offline version, because the whole of its value is that nobody — not
 * even the app — knows what happens next.
 */
@Composable
internal fun StoryTool(
    state: ArcadeUiState,
    coachReady: Boolean,
    onStart: (genre: String) -> Unit,
    onChoose: (String) -> Unit,
    onReset: () -> Unit,
    onRoundComplete: () -> Unit,
    onClose: () -> Unit,
) {
    val motion = LocalMotion.current
    val story = state.story
    val current = story.current
    var genre by rememberSaveable { mutableStateOf(ArcadeOffline.storyGenres.first()) }
    val ended = current?.ending == true
    var recorded by rememberSaveable(story.genre, story.beats.size) { mutableStateOf(false) }

    LaunchedEffect(story.beats.size, ended) {
        if (ended && !recorded) {
            recorded = true
            onRoundComplete()
        }
    }

    ToolShell(
        title = stringResource(R.string.story_title),
        evidence = stringResource(R.string.story_evidence),
        onClose = onClose,
    ) {
        when {
            !coachReady -> {
                Text(stringResource(R.string.story_needs_coach), style = MaterialTheme.typography.bodyMedium)
            }

            current == null && state.busy -> ArcadeLoading(stringResource(R.string.story_loading))

            current == null -> {
                Text(stringResource(R.string.story_pick_genre), style = MaterialTheme.typography.titleSmall)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    ArcadeOffline.storyGenres.forEach { option ->
                        FilterChip(
                            selected = genre == option,
                            onClick = { genre = option },
                            label = { Text(option) },
                        )
                    }
                }
                Button(onClick = { onStart(genre) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.AutoStories, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.story_begin))
                }
            }

            else -> {
                Text(
                    if (ended) {
                        stringResource(R.string.story_the_end)
                    } else {
                        stringResource(R.string.story_beat, story.beats.size, ArcadeOffline.STORY_BEATS)
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                LinearProgressIndicator(
                    progress = { (story.beats.size.toFloat() / ArcadeOffline.STORY_BEATS).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
                AnimatedContent(
                    targetState = current.scene,
                    transitionSpec = { fadeIn(motion.eased(200)) togetherWith fadeOut(motion.eased(160)) },
                    label = "storyScene",
                ) { scene ->
                    Text(scene, style = MaterialTheme.typography.bodyLarge)
                }
                when {
                    state.busy -> ArcadeLoading(stringResource(R.string.story_turning))
                    ended -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = onReset, modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.story_new))
                            }
                            OutlinedButton(onClick = onClose, modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.arcade_done))
                            }
                        }
                    }
                    else -> current.choices.forEach { choice ->
                        FilledTonalButton(onClick = { onChoose(choice) }, modifier = Modifier.fillMaxWidth()) {
                            Text(choice, modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
                        }
                    }
                }
            }
        }
        state.error?.let { ArcadeError(it) }
    }
}

@Composable
private fun RoundProgress(index: Int, total: Int, offline: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.arcade_progress, index + 1, total),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        if (offline) {
            Text(
                stringResource(R.string.arcade_offline_round),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    LinearProgressIndicator(
        progress = { (index.toFloat() / total.coerceAtLeast(1)).coerceIn(0f, 1f) },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ArcadeResult(
    score: Int,
    total: Int,
    offline: Boolean,
    busy: Boolean,
    onAgain: () -> Unit,
    onClose: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            stringResource(R.string.arcade_score, score, total),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            stringResource(
                when {
                    score == total -> R.string.arcade_score_perfect
                    score * 2 >= total -> R.string.arcade_score_good
                    else -> R.string.arcade_score_any
                },
            ),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (offline) OfflineNote(stringResource(R.string.arcade_offline_round))
    }
    if (busy) {
        ArcadeLoading(stringResource(R.string.arcade_dealing))
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onAgain, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.arcade_again))
            }
            OutlinedButton(onClick = onClose, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.arcade_done))
            }
        }
    }
}

@Composable
private fun ArcadeLoading(label: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        Spacer(Modifier.size(12.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun OfflineNote(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ArcadeError(message: String) {
    Text(
        message,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
    )
}
