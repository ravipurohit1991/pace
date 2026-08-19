package com.pace.reduction.feature

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Coffee
import androidx.compose.material.icons.outlined.LocalBar
import androidx.compose.material.icons.outlined.LocalDrink
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pace.reduction.R
import com.pace.reduction.domain.BeverageCounts
import com.pace.reduction.domain.model.BeverageType

private data class BeverageOption(
    val type: BeverageType,
    val label: Int,
    val icon: ImageVector,
)

private val BeverageOptions = listOf(
    BeverageOption(BeverageType.COFFEE, R.string.beverage_coffee, Icons.Outlined.Coffee),
    BeverageOption(BeverageType.ALCOHOL, R.string.beverage_alcohol, Icons.Outlined.LocalBar),
    BeverageOption(BeverageType.OTHER, R.string.beverage_other, Icons.Outlined.LocalDrink),
)

/** A compact, low-friction companion log that stays separate from the reduction plan. */
@Composable
internal fun BeverageTrackerCard(
    counts: BeverageCounts,
    onLog: (BeverageType) -> Unit,
    hapticsEnabled: Boolean,
    enabledTypes: Set<BeverageType>,
    otherLabel: String,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    SectionCard(modifier) {
        Text(stringResource(R.string.beverages_title), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.beverages_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BeverageOptions.filter { it.type in enabledTypes }.forEach { option ->
                val label = if (option.type == BeverageType.OTHER) otherLabel else stringResource(option.label)
                val count = counts[option.type]
                val description = stringResource(R.string.beverage_add_accessible, label, count)
                FilledTonalButton(
                    onClick = {
                        if (hapticsEnabled) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        onLog(option.type)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 86.dp)
                        .semantics { contentDescription = description },
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 10.dp),
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Icon(option.icon, contentDescription = null, modifier = Modifier.size(20.dp))
                        Text(
                            count.toString(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            label,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}
