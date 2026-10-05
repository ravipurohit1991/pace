package com.pace.reduction.feature

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pace.reduction.R
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CheckInSheet(
    onDismiss: () -> Unit,
    onSave: suspend (Int?, Set<String>, String) -> Boolean,
    onOpenToolkit: () -> Unit,
) {
    var strength by rememberSaveable { mutableIntStateOf(3) }
    var tags by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var note by rememberSaveable { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var saved by rememberSaveable { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    ModalBottomSheet(
        onDismissRequest = { if (!saving) onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (saved) {
                Box(
                    Modifier.align(Alignment.CenterHorizontally).size(72.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Outlined.Check, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary) }
                Text(stringResource(R.string.checkin_saved_title), style = MaterialTheme.typography.headlineMedium)
                Text(stringResource(R.string.checkin_saved_body), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.checkin_done)) }
                OutlinedButton(onClick = onOpenToolkit, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.insights_open_toolkit)) }
            } else {
                Icon(Icons.Outlined.Spa, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                Text(stringResource(R.string.checkin_title), style = MaterialTheme.typography.headlineMedium)
                Text(stringResource(R.string.checkin_body), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.checkin_strength), style = MaterialTheme.typography.titleMedium)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (1..5).forEach { value ->
                        val description = stringResource(R.string.checkin_rating, value)
                        FilterChip(
                            selected = strength == value,
                            onClick = { strength = value },
                            enabled = !saving,
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp).semantics { contentDescription = description },
                            label = { Text(value.toString(), Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
                        )
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.checkin_low), style = MaterialTheme.typography.labelMedium)
                    Text(stringResource(R.string.checkin_high), style = MaterialTheme.typography.labelMedium)
                }
                HorizontalDivider()
                Column {
                    Text(stringResource(R.string.checkin_triggers), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.checkin_optional), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    checkInTriggers.forEach { (tag, label) ->
                        FilterChip(
                            selected = tag in tags, enabled = !saving,
                            onClick = { tags = if (tag in tags) tags - tag else tags + tag },
                            label = { Text(stringResource(label)) },
                        )
                    }
                }
                OutlinedTextField(
                    value = note, onValueChange = { note = it.take(500) }, enabled = !saving,
                    label = { Text(stringResource(R.string.checkin_note)) },
                    supportingText = { Text("${note.length}/500") },
                    modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 4,
                )
                if (failed) Text(stringResource(R.string.checkin_error), color = MaterialTheme.colorScheme.error)
                Button(
                    enabled = !saving,
                    onClick = {
                        saving = true
                        failed = false
                        scope.launch {
                            try {
                                saved = onSave(strength, tags.toSet(), note)
                                failed = !saved
                            } finally { saving = false }
                        }
                    }, modifier = Modifier.fillMaxWidth(),
                ) {
                    if (saving) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Text(stringResource(R.string.checkin_save))
                }
            }
        }
    }
}

internal val checkInTriggers = listOf(
    "stress" to R.string.checkin_stress,
    "coffee" to R.string.checkin_coffee,
    "social" to R.string.checkin_social,
    "boredom" to R.string.checkin_boredom,
    "after_meal" to R.string.checkin_after_meal,
    "alcohol" to R.string.checkin_alcohol,
    "routine" to R.string.checkin_routine,
)
