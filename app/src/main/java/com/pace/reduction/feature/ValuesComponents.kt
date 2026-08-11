package com.pace.reduction.feature

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pace.reduction.R
import com.pace.reduction.domain.model.PlanSettings

/**
 * The short lines a person wrote about what this is actually for.
 *
 * There is already a free-text reason on this screen, and it is the wrong shape for the moment it
 * matters: nobody reads a paragraph at minute two of a bad afternoon. Values are three or four
 * fragments — "be there when she's twenty", "stop being out of breath on the stairs" — short enough
 * to land in the second somebody has spare, and they are what the coach reaches for instead of
 * encouragement.
 */
@Composable
internal fun ValuesEditor(values: List<String>, onChange: (List<String>) -> Unit) {
    var draft by rememberSaveable { mutableStateOf("") }
    val suggestions = stringArrayResource(R.array.value_suggestions)
    val full = values.size >= PlanSettings.MAX_VALUES

    fun add(candidate: String) {
        val cleaned = candidate.trim().replace(Regex("\\s+"), " ").take(PlanSettings.MAX_VALUE_CHARS)
        if (cleaned.isEmpty() || full || values.any { it.equals(cleaned, ignoreCase = true) }) return
        onChange(values + cleaned)
        draft = ""
    }

    SectionCard {
        Text(stringResource(R.string.values_title), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.values_body, PlanSettings.MAX_VALUES),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (values.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                values.forEach { value ->
                    InputChip(
                        selected = false,
                        onClick = { onChange(values - value) },
                        label = { Text(value) },
                        trailingIcon = {
                            Icon(
                                Icons.Outlined.Close,
                                contentDescription = stringResource(R.string.values_remove, value),
                                modifier = Modifier.size(16.dp),
                            )
                        },
                    )
                }
            }
        }
        if (!full) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it.take(PlanSettings.MAX_VALUE_CHARS) },
                    modifier = Modifier.weight(1f),
                    label = { Text(stringResource(R.string.values_add_label)) },
                    singleLine = true,
                )
                IconButton(onClick = { add(draft) }, enabled = draft.isNotBlank()) {
                    Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.values_add))
                }
            }
            // Starters rather than a menu: the ones people pick get edited into their own words,
            // and a blank box is a hard thing to answer honestly on the first try.
            val unused = suggestions.filterNot { suggestion ->
                values.any { it.equals(suggestion, ignoreCase = true) }
            }
            if (unused.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    unused.take(4).forEach { suggestion ->
                        AssistChip(onClick = { add(suggestion) }, label = { Text(suggestion) })
                    }
                }
            }
        }
    }
}

/** The same lines, read-only, wherever they are worth being in front of somebody. */
@Composable
internal fun ValuesChips(values: List<String>, modifier: Modifier = Modifier) {
    if (values.isEmpty()) return
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        values.forEach { value ->
            AssistChip(onClick = {}, enabled = false, label = { Text(value) })
        }
    }
}
