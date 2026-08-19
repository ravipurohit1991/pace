package com.pace.reduction.feature

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.EditCalendar
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pace.reduction.BuildConfig
import com.pace.reduction.PaceUiState
import com.pace.reduction.PaceViewModel
import com.pace.reduction.R
import com.pace.reduction.core.network.OllamaClient
import com.pace.reduction.domain.model.PlanSettings
import com.pace.reduction.domain.model.ReminderIntensity
import com.pace.reduction.widget.PaceWidget
import com.pace.reduction.widget.PaceWidgetReceiver
import java.time.LocalDate
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsScreen(
    uiState: PaceUiState,
    viewModel: PaceViewModel,
    onSave: (PlanSettings) -> Unit,
    onExport: (android.net.Uri) -> Unit,
    onImport: (android.net.Uri) -> Unit,
    onDeleteAll: () -> Unit,
    onOpenHistory: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var reminder by rememberSaveable(uiState.settings.reminderIntensity) { mutableStateOf(uiState.settings.reminderIntensity) }
    var haptics by rememberSaveable(uiState.settings.hapticsEnabled) { mutableStateOf(uiState.settings.hapticsEnabled) }
    var privateOnLockScreen by rememberSaveable(uiState.settings.notificationPrivate) {
        mutableStateOf(uiState.settings.notificationPrivate)
    }
    var drinkQuickLog by rememberSaveable(uiState.settings.drinkQuickLogEnabled) {
        mutableStateOf(uiState.settings.drinkQuickLogEnabled)
    }
    var coffeeTracking by rememberSaveable(uiState.settings.coffeeTrackingEnabled) {
        mutableStateOf(uiState.settings.coffeeTrackingEnabled)
    }
    var alcoholTracking by rememberSaveable(uiState.settings.alcoholTrackingEnabled) {
        mutableStateOf(uiState.settings.alcoholTrackingEnabled)
    }
    var otherTracking by rememberSaveable(uiState.settings.otherBeverageTrackingEnabled) {
        mutableStateOf(uiState.settings.otherBeverageTrackingEnabled)
    }
    var otherLabel by rememberSaveable(uiState.settings.otherBeverageLabel) {
        mutableStateOf(uiState.settings.otherBeverageLabel)
    }
    var deleteStepTwo by rememberSaveable { mutableStateOf(false) }
    var pendingImport by remember { mutableStateOf<android.net.Uri?>(null) }
    var notificationEducation by rememberSaveable { mutableStateOf(false) }
    var widgetInstalled by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(Unit) {
        widgetInstalled = GlanceAppWidgetManager(context)
            .getGlanceIds(PaceWidget::class.java)
            .isNotEmpty()
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let(onExport) }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> pendingImport = uri }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val notificationsGranted = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    PaceScreen(
        title = stringResource(R.string.settings_title),
        onBack = onBack,
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Appearance changes as you tap it, not on a later Save — a colour you have to commit
        // to before seeing is a colour you cannot choose.
        item { AppearanceSection(uiState.settings, viewModel::saveAppearance) }
        item {
            WidgetSection(
                widget = uiState.widget,
                accent = uiState.settings.accentPalette,
                dynamicColor = uiState.settings.dynamicColor,
                installed = widgetInstalled,
                onRequestPin = {
                    scope.launch {
                        GlanceAppWidgetManager(context).requestPinGlanceAppWidget(
                            receiver = PaceWidgetReceiver::class.java,
                            preview = PaceWidget(),
                        )
                    }
                },
                onChange = viewModel::saveWidgetSettings,
            )
        }
        item {
            TrackingSection(
                quickLog = drinkQuickLog,
                coffee = coffeeTracking,
                alcohol = alcoholTracking,
                other = otherTracking,
                otherLabel = otherLabel,
                onQuickLogChange = { drinkQuickLog = it },
                onCoffeeChange = { coffeeTracking = it },
                onAlcoholChange = { alcoholTracking = it },
                onOtherChange = { otherTracking = it },
                onOtherLabelChange = {
                    if (it.length <= PlanSettings.MAX_TRACKER_LABEL_CHARS) otherLabel = it
                },
            )
        }
        item {
            SectionCard {
                Text(stringResource(R.string.notifications_title), style = MaterialTheme.typography.titleLarge)
                Text(
                    stringResource(if (notificationsGranted) R.string.notifications_granted else R.string.notifications_not_granted),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReminderIntensity.entries.forEach { option ->
                        FilterChip(
                            selected = reminder == option,
                            onClick = { reminder = option },
                            label = {
                                Text(
                                    stringResource(
                                        when (option) {
                                            ReminderIntensity.OFF -> R.string.reminder_off
                                            ReminderIntensity.GENTLE -> R.string.reminder_gentle
                                            ReminderIntensity.STANDARD -> R.string.reminder_standard
                                        },
                                    ),
                                )
                            },
                        )
                    }
                }
                if (!notificationsGranted && !notificationEducation) {
                    OutlinedButton(onClick = { notificationEducation = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.Notifications, contentDescription = null)
                        Text(stringResource(R.string.explain_notifications))
                    }
                } else if (!notificationsGranted) {
                    Text(stringResource(R.string.notification_education_body))
                    Button(
                        onClick = { notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.enable_notifications)) }
                }
                OutlinedButton(
                    onClick = {
                        context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        })
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.open_system_notification_settings)) }
                SettingSwitch(
                    title = stringResource(R.string.haptics),
                    body = stringResource(R.string.haptics_body),
                    checked = haptics,
                    onCheckedChange = { haptics = it },
                )
                SettingSwitch(
                    title = stringResource(R.string.notification_private_title),
                    body = stringResource(R.string.notification_private_body),
                    checked = privateOnLockScreen,
                    onCheckedChange = { privateOnLockScreen = it },
                )
            }
        }
        item { AiCoachSection(uiState, viewModel) }
        item {
            Button(
                onClick = {
                    onSave(
                        uiState.settings.copy(
                            reminderIntensity = reminder,
                            hapticsEnabled = haptics,
                            notificationPrivate = privateOnLockScreen,
                            drinkQuickLogEnabled = drinkQuickLog,
                            coffeeTrackingEnabled = coffeeTracking,
                            alcoholTrackingEnabled = alcoholTracking,
                            otherBeverageTrackingEnabled = otherTracking,
                            otherBeverageLabel = otherLabel.trim().ifBlank { "Other" },
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.save_settings)) }
        }
        item {
            SectionCard {
                Text(stringResource(R.string.history_open), style = MaterialTheme.typography.titleLarge)
                Text(
                    stringResource(R.string.history_open_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = onOpenHistory, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.EditCalendar, contentDescription = null)
                    Text(stringResource(R.string.history_title))
                }
            }
        }
        item {
            SectionCard {
                Text(stringResource(R.string.data_controls_title), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.data_controls_body))
                OutlinedButton(
                    onClick = { exportLauncher.launch("pace-backup-${LocalDate.now()}.json") },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.FileUpload, contentDescription = null)
                    Text(stringResource(R.string.export_json))
                }
                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("application/json", "text/plain")) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.FileDownload, contentDescription = null)
                    Text(stringResource(R.string.import_json))
                }
                if (pendingImport != null) {
                    Text(stringResource(R.string.import_confirmation))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            pendingImport?.let(onImport)
                            pendingImport = null
                        }) { Text(stringResource(R.string.import_confirm)) }
                        OutlinedButton(onClick = { pendingImport = null }) { Text(stringResource(R.string.cancel)) }
                    }
                }
            }
        }
        item {
            SectionCard {
                Text(stringResource(R.string.privacy_title), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.privacy_network_summary))
                Text(stringResource(R.string.privacy_summary))
                Text(stringResource(R.string.medical_disclaimer))
                Text(stringResource(R.string.licenses_summary), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.version_value, BuildConfig.VERSION_NAME, BuildConfig.BUILD_TYPE))
            }
        }
        item {
            SectionCard {
                Text(
                    stringResource(R.string.delete_all_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.error,
                )
                Text(stringResource(R.string.delete_all_body))
                if (!deleteStepTwo) {
                    OutlinedButton(onClick = { deleteStepTwo = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.DeleteForever, contentDescription = null)
                        Text(stringResource(R.string.delete_all_first_step))
                    }
                } else {
                    Text(stringResource(R.string.delete_all_confirmation), color = MaterialTheme.colorScheme.error)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onDeleteAll, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.delete_all_confirm))
                        }
                        OutlinedButton(onClick = { deleteStepTwo = false }, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.cancel))
                        }
                    }
                }
            }
        }
        }
}

@Composable
private fun TrackingSection(
    quickLog: Boolean,
    coffee: Boolean,
    alcohol: Boolean,
    other: Boolean,
    otherLabel: String,
    onQuickLogChange: (Boolean) -> Unit,
    onCoffeeChange: (Boolean) -> Unit,
    onAlcoholChange: (Boolean) -> Unit,
    onOtherChange: (Boolean) -> Unit,
    onOtherLabelChange: (String) -> Unit,
) {
    SectionCard {
        Text(stringResource(R.string.tracking_settings_title), style = MaterialTheme.typography.titleLarge)
        Text(
            stringResource(R.string.tracking_settings_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SettingSwitch(
            title = stringResource(R.string.tracking_quick_log),
            body = stringResource(R.string.tracking_quick_log_body),
            checked = quickLog,
            onCheckedChange = onQuickLogChange,
        )
        SettingSwitch(
            title = stringResource(R.string.beverage_coffee),
            body = stringResource(R.string.tracking_coffee_body),
            checked = coffee,
            onCheckedChange = onCoffeeChange,
        )
        SettingSwitch(
            title = stringResource(R.string.beverage_alcohol),
            body = stringResource(R.string.tracking_alcohol_body),
            checked = alcohol,
            onCheckedChange = onAlcoholChange,
        )
        SettingSwitch(
            title = stringResource(R.string.tracking_other_title),
            body = stringResource(R.string.tracking_other_body),
            checked = other,
            onCheckedChange = onOtherChange,
        )
        if (other) {
            OutlinedTextField(
                value = otherLabel,
                onValueChange = onOtherLabelChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.tracking_other_label)) },
                supportingText = {
                    Text(
                        stringResource(
                            R.string.tracker_character_count,
                            otherLabel.length,
                            PlanSettings.MAX_TRACKER_LABEL_CHARS,
                        ),
                    )
                },
                singleLine = true,
            )
        }
        Text(
            stringResource(R.string.tracking_preserves_history),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Ollama Cloud key entry, model picker and nudge toggle. */
@Composable
private fun AiCoachSection(uiState: PaceUiState, viewModel: PaceViewModel) {
    val coach by viewModel.coachState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val saved = uiState.ai
    var enabled by rememberSaveable(saved.enabled) { mutableStateOf(saved.enabled) }
    var nudges by rememberSaveable(saved.proactiveNudges) { mutableStateOf(saved.proactiveNudges) }
    var model by rememberSaveable(saved.model) { mutableStateOf(saved.model) }
    var apiKey by rememberSaveable { mutableStateOf("") }
    var revealKey by rememberSaveable { mutableStateOf(false) }

    val verifiedModels = coach.verifiedModels
    val compatibleModels = verifiedModels.ifEmpty { listOf(OllamaClient.DEFAULT_MODEL) }
    LaunchedEffect(compatibleModels) {
        if (model !in compatibleModels) model = compatibleModels.first()
    }

    SectionCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.ai_settings_title), style = MaterialTheme.typography.titleLarge)
                Text(
                    stringResource(R.string.ai_settings_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = enabled, onCheckedChange = { enabled = it })
        }

        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it.trim().take(256) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.ai_api_key)) },
            placeholder = { Text(if (saved.apiKey.isNotBlank()) "••••••••" else "") },
            supportingText = {
                if (saved.apiKey.isNotBlank() && apiKey.isBlank()) {
                    Text(stringResource(R.string.ai_api_key_saved))
                }
            },
            visualTransformation = if (revealKey) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { revealKey = !revealKey }) {
                    Icon(
                        if (revealKey) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription = null,
                    )
                }
            },
            singleLine = true,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { viewModel.verifyApiKey(apiKey.ifBlank { saved.apiKey }) },
                enabled = !coach.verifying && (apiKey.isNotBlank() || saved.apiKey.isNotBlank()),
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(if (coach.verifying) R.string.ai_verifying else R.string.ai_verify))
            }
            OutlinedButton(
                onClick = { openTrustedTab(context, "https://ollama.com/settings/keys") },
                modifier = Modifier.weight(1f),
            ) { Text(stringResource(R.string.ai_get_key)) }
        }

        coach.error?.let { error ->
            Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }

        if (verifiedModels.isNotEmpty()) {
            Text(
                stringResource(R.string.ai_models_verified, verifiedModels.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        ModelChoice(
            title = stringResource(R.string.ai_multimodal_model),
            supporting = stringResource(R.string.ai_multimodal_model_body),
            value = model,
            options = compatibleModels,
            onValueChange = { model = it },
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.ai_nudges), modifier = Modifier.weight(1f))
            Switch(checked = nudges, onCheckedChange = { nudges = it })
        }

        Button(
            onClick = {
                viewModel.saveAiSettings(enabled, apiKey, model, model, nudges)
                apiKey = ""
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.save_settings)) }

        if (saved.apiKey.isNotBlank()) {
            OutlinedButton(onClick = viewModel::clearApiKey, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.ai_clear_key))
            }
        }
    }
}

@Composable
private fun ModelChoice(
    title: String,
    supporting: String,
    value: String,
    options: List<String>,
    onValueChange: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(
            supporting,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(value, modifier = Modifier.weight(1f))
                Icon(Icons.Outlined.ExpandMore, contentDescription = null)
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            onValueChange(option)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}
