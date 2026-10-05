package com.pace.reduction.feature

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pace.reduction.PaceUiState
import com.pace.reduction.R
import com.pace.reduction.domain.SavingsProjection
import java.math.BigDecimal
import kotlin.math.roundToInt

@Composable
internal fun SavingsLab(uiState: PaceUiState, onOpenPlan: () -> Unit) {
    val plan = uiState.settings
    val locale = LocalConfiguration.current.locales[0]
    val baseline = plan.baselinePerDay.coerceAtLeast(1)
    var fewer by rememberSaveable { mutableIntStateOf((plan.baselinePerDay - plan.dailyCeiling).coerceIn(1, baseline)) }
    var horizon by rememberSaveable { mutableIntStateOf(30) }
    val reduction = fewer.coerceIn(0, baseline)
    val estimate = SavingsProjection.estimate(reduction, horizon, plan.pricePerPack, plan.cigarettesPerPack)
    val sliderLabel = stringResource(R.string.savings_fewer, reduction)
    val projectedLabel = stringResource(R.string.savings_scenario, horizon)
    val accent = MaterialTheme.colorScheme.primary
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        InsightFeatureSurface {
            Icon(Icons.Outlined.Savings, null, Modifier.size(28.dp))
            Text(stringResource(R.string.savings_eyebrow), style = MaterialTheme.typography.labelSmall, letterSpacing = 1.3.sp)
            Text(stringResource(R.string.savings_title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.savings_body), style = MaterialTheme.typography.bodyMedium)
        }
        if (plan.pricePerPack <= 0 || plan.cigarettesPerPack <= 0) {
            SectionCard {
                Text(stringResource(R.string.savings_price_needed), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.savings_price_body), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = onOpenPlan) { Text(stringResource(R.string.savings_edit_plan)) }
            }
        } else {
            SectionCard {
                Text(sliderLabel, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.savings_from_baseline, plan.baselinePerDay), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Slider(value = reduction.toFloat(), onValueChange = { fewer = it.roundToInt() }, valueRange = 0f..baseline.toFloat(),
                    steps = (baseline - 1).coerceAtLeast(0), modifier = Modifier.fillMaxWidth().semantics { contentDescription = sliderLabel })
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf(30 to R.string.savings_30, 90 to R.string.savings_90, 365 to R.string.savings_365).forEachIndexed { index, (days, label) ->
                        SegmentedButton(selected = horizon == days, onClick = { horizon = days }, shape = SegmentedButtonDefaults.itemShape(index, 3), icon = {}) {
                            Text(stringResource(label))
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(moneyText(estimate, plan.currencyCode, locale), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Medium)
                Text(projectedLabel, style = MaterialTheme.typography.bodyMedium)
                val chartDescription = stringResource(R.string.savings_chart_description, moneyText(estimate, plan.currencyCode, locale), horizon)
                Canvas(Modifier.fillMaxWidth().height(110.dp).semantics { contentDescription = chartDescription }) {
                    val start = Offset(0f, size.height - 4.dp.toPx())
                    val end = Offset(size.width, if (reduction == 0) start.y else 8.dp.toPx())
                    val fill = Path().apply { moveTo(start.x, start.y); lineTo(end.x, end.y); lineTo(size.width, size.height); lineTo(0f, size.height); close() }
                    drawPath(fill, Brush.verticalGradient(listOf(accent.copy(alpha = 0.24f), accent.copy(alpha = 0.02f))))
                    drawLine(accent, start, end, strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
                    drawCircle(accent, 4.dp.toPx(), end)
                }
                Text(pluralStringResource(R.plurals.savings_avoided, reduction * horizon, reduction * horizon), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.savings_assumption), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (plan.rewardTarget > 0) {
            val saved = uiState.progress?.estimatedSavings ?: BigDecimal.ZERO
            val days = SavingsProjection.daysToReward(plan.rewardTarget - saved.toDouble(), reduction, plan.pricePerPack, plan.cigarettesPerPack)
            SectionCard {
                Text(stringResource(R.string.savings_reward), style = MaterialTheme.typography.labelSmall, color = accent, letterSpacing = 1.3.sp)
                Text(plan.rewardName.ifBlank { stringResource(R.string.your_reward) }, style = MaterialTheme.typography.headlineSmall)
                LinearProgressIndicator(progress = { (saved.toDouble() / plan.rewardTarget).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(8.dp))
                Text(stringResource(R.string.savings_reward_progress, moneyText(saved, plan.currencyCode, locale),
                    moneyText(BigDecimal.valueOf(plan.rewardTarget), plan.currencyCode, locale)), style = MaterialTheme.typography.bodyMedium)
                Text(when (days) {
                    null -> stringResource(R.string.savings_reward_wait)
                    0L -> stringResource(R.string.savings_reward_complete)
                    else -> pluralStringResource(R.plurals.savings_reward_days, days.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), days)
                }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = onOpenPlan) { Text(stringResource(R.string.savings_edit_plan)) }
            }
        } else {
            InsightActionCard(Icons.Outlined.Savings, stringResource(R.string.savings_set_reward), stringResource(R.string.savings_set_reward_body), onOpenPlan)
        }
    }
}
