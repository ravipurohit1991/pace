package com.pace.reduction.feature

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.pace.reduction.R
import com.pace.reduction.domain.model.CoachingTone
import com.pace.reduction.domain.model.PlanSettings
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Composable
internal fun GuidedOnboardingScreen(initial: PlanSettings, onFinish: (PlanSettings) -> Unit) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    var baseline by rememberSaveable { mutableStateOf(initial.baselinePerDay.toString()) }
    var ceiling by rememberSaveable { mutableStateOf(initial.dailyCeiling.toString()) }
    var gap by rememberSaveable { mutableStateOf(initial.minimumGapMinutes.toString()) }
    var wake by rememberSaveable { mutableStateOf(onboardingTime(initial.wakeMinutes)) }
    var sleep by rememberSaveable { mutableStateOf(onboardingTime(initial.sleepMinutes)) }
    var weekendEnabled by rememberSaveable { mutableStateOf(initial.weekendWakeEnabled) }
    var weekendWake by rememberSaveable { mutableStateOf(onboardingTime(initial.weekendWakeMinutes)) }
    var hold by rememberSaveable { mutableStateOf(initial.morningHoldMinutes.toString()) }
    var price by rememberSaveable { mutableStateOf("") }
    var packSize by rememberSaveable { mutableStateOf(initial.cigarettesPerPack.toString()) }
    var reason by rememberSaveable { mutableStateOf(initial.personalReason) }
    var rewardName by rememberSaveable { mutableStateOf(initial.rewardName) }
    var rewardTarget by rememberSaveable { mutableStateOf("") }
    var tone by rememberSaveable { mutableStateOf(initial.coachingTone) }

    val startingValid = baseline.toIntOrNull() in 1..100 && ceiling.toIntOrNull() in 0..100 &&
        packSize.toIntOrNull() in 1..100 && (price.toDoubleOrNull() ?: 0.0) >= 0.0
    val routineValid = gap.toIntOrNull() in 15..360 && hold.toIntOrNull() in 0..240 &&
        parseOnboardingTime(wake) != null && parseOnboardingTime(sleep) != null && wake != sleep &&
        (!weekendEnabled || parseOnboardingTime(weekendWake) != null)
    val canContinue = when (page) {
        1 -> startingValid
        2 -> routineValid
        else -> true
    }

    fun resultPlan() = initial.copy(
        baselinePerDay = baseline.toIntOrNull() ?: 20,
        dailyCeiling = ceiling.toIntOrNull() ?: 18,
        minimumGapMinutes = gap.toIntOrNull() ?: 60,
        wakeMinutes = parseOnboardingTime(wake) ?: 7 * 60,
        sleepMinutes = parseOnboardingTime(sleep) ?: 22 * 60 + 30,
        weekendWakeEnabled = weekendEnabled,
        weekendWakeMinutes = parseOnboardingTime(weekendWake) ?: 8 * 60,
        morningHoldMinutes = hold.toIntOrNull() ?: 30,
        pricePerPack = price.toDoubleOrNull() ?: 0.0,
        cigarettesPerPack = packSize.toIntOrNull() ?: 20,
        personalReason = reason,
        rewardName = rewardName,
        rewardTarget = rewardTarget.toDoubleOrNull() ?: 0.0,
        coachingTone = tone,
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 40.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Text(stringResource(R.string.onboarding_step, page + 1), style = MaterialTheme.typography.labelLarge)
            Text(
                stringResource(
                    when (page) {
                        0 -> R.string.onboarding_intent_title
                        1 -> R.string.onboarding_start_title
                        2 -> R.string.onboarding_routine_title
                        3 -> R.string.onboarding_motivation_title
                        else -> R.string.onboarding_ready_title
                    },
                ),
                style = MaterialTheme.typography.displaySmall,
            )
        }
        when (page) {
            0 -> {
                item { Text(stringResource(R.string.onboarding_intent_body), style = MaterialTheme.typography.bodyLarge) }
                item { SectionCard { Text(stringResource(R.string.medical_disclaimer)) } }
                item {
                    OutlinedButton(
                        onClick = {
                            baseline = "20"
                            ceiling = "18"
                            gap = "60"
                            wake = "07:00"
                            sleep = "22:30"
                            hold = "30"
                            tone = CoachingTone.SUPPORTIVE
                            page = 4
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.use_safe_defaults)) }
                }
                item { Text(stringResource(R.string.defaults_disclaimer), style = MaterialTheme.typography.bodySmall) }
            }
            1 -> {
                item { OnboardingNumberField(R.string.baseline_label, baseline, { baseline = it }, 1..100) }
                item { OnboardingNumberField(R.string.ceiling_label, ceiling, { ceiling = it }, 0..100) }
                item { OnboardingDecimalField(R.string.price_per_pack, price, { price = it }) }
                item { OnboardingNumberField(R.string.cigarettes_per_pack, packSize, { packSize = it }, 1..100) }
                item { SectionCard { Text(stringResource(R.string.ceiling_not_quota)) } }
            }
            2 -> {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OnboardingTimeField(R.string.wake_label, wake, { wake = it }, Modifier.weight(1f))
                        OnboardingTimeField(R.string.sleep_label, sleep, { sleep = it }, Modifier.weight(1f))
                    }
                }
                item { OnboardingNumberField(R.string.spacing_label, gap, { gap = it }, 15..360) }
                item { OnboardingNumberField(R.string.morning_hold_label, hold, { hold = it }, 0..240) }
                item {
                    SectionCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.weekend_wake), modifier = Modifier.weight(1f))
                            Switch(checked = weekendEnabled, onCheckedChange = { weekendEnabled = it })
                        }
                        if (weekendEnabled) {
                            OnboardingTimeField(R.string.weekend_wake_time, weekendWake, { weekendWake = it }, Modifier.fillMaxWidth())
                        }
                    }
                }
            }
            3 -> {
                item {
                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it.take(500) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.personal_reason_label)) },
                        supportingText = { Text(stringResource(R.string.personal_reason_support)) },
                        minLines = 2,
                    )
                }
                item {
                    OutlinedTextField(
                        value = rewardName,
                        onValueChange = { rewardName = it.take(100) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.reward_name)) },
                    )
                }
                item { OnboardingDecimalField(R.string.reward_target, rewardTarget, { rewardTarget = it }) }
                item {
                    Text(stringResource(R.string.coaching_voice), style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CoachingTone.entries.forEach { option ->
                            FilterChip(
                                selected = tone == option,
                                onClick = { tone = option },
                                label = {
                                    Text(
                                        stringResource(
                                            when (option) {
                                                CoachingTone.SUPPORTIVE -> R.string.voice_supportive
                                                CoachingTone.DIRECT -> R.string.voice_direct
                                                CoachingTone.TOUGH -> R.string.voice_tough
                                            },
                                        ),
                                    )
                                },
                            )
                        }
                    }
                }
            }
            else -> {
                item { Text(stringResource(R.string.onboarding_ready_body)) }
                item {
                    SectionCard {
                        Text(stringResource(R.string.plan_summary_ceiling, ceiling.toIntOrNull() ?: 18))
                        Text(stringResource(R.string.plan_summary_gap, gap.toIntOrNull() ?: 60))
                        Text(stringResource(R.string.plan_summary_quiet, sleep, wake))
                        Text(stringResource(R.string.plan_summary_hold, hold.toIntOrNull() ?: 30))
                    }
                }
                item { Text(stringResource(R.string.onboarding_permissions_note)) }
            }
        }
        item { Spacer(Modifier.height(4.dp)) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (page > 0) {
                    OutlinedButton(onClick = { page-- }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.back))
                    }
                }
                Button(
                    onClick = { if (page < 4) page++ else onFinish(resultPlan()) },
                    enabled = canContinue,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(if (page < 4) R.string.next else R.string.create_my_plan))
                }
            }
        }
    }
}

@Composable
private fun OnboardingNumberField(label: Int, value: String, onChange: (String) -> Unit, range: IntRange) {
    val parsed = value.toIntOrNull()
    OutlinedTextField(
        value = value,
        onValueChange = { if (it.length <= 3 && it.all(Char::isDigit)) onChange(it) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(label)) },
        supportingText = { Text(stringResource(R.string.allowed_range, range.first, range.last)) },
        isError = parsed == null || parsed !in range,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
    )
}

@Composable
private fun OnboardingDecimalField(label: Int, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { entered -> if (entered.length <= 10 && entered.all { it.isDigit() || it == '.' }) onChange(entered) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(label)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
    )
}

@Composable
private fun OnboardingTimeField(label: Int, value: String, onChange: (String) -> Unit, modifier: Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = { if (it.length <= 5) onChange(it) },
        modifier = modifier,
        label = { Text(stringResource(label)) },
        isError = parseOnboardingTime(value) == null,
        supportingText = { Text(stringResource(R.string.time_format)) },
        singleLine = true,
    )
}

private fun parseOnboardingTime(value: String): Int? = runCatching {
    LocalTime.parse(value, DateTimeFormatter.ofPattern("HH:mm")).let { it.hour * 60 + it.minute }
}.getOrNull()

private fun onboardingTime(minutes: Int): String = "%02d:%02d".format(minutes / 60, minutes % 60)
