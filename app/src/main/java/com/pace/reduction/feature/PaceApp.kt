package com.pace.reduction.feature

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddCircle
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.activity.compose.LocalActivity
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import androidx.glance.appwidget.GlanceAppWidgetManager
import com.pace.reduction.PaceEvent
import com.pace.reduction.PaceUiState
import com.pace.reduction.PaceViewModel
import com.pace.reduction.R
import com.pace.reduction.core.designsystem.LocalMotion
import com.pace.reduction.core.designsystem.breathe
import com.pace.reduction.core.designsystem.entrance
import com.pace.reduction.core.designsystem.pressScale
import com.pace.reduction.core.designsystem.pulseAlpha
import com.pace.reduction.domain.BadgeCatalogue
import com.pace.reduction.domain.PacingCalculator
import com.pace.reduction.domain.ReductionPlanner
import com.pace.reduction.domain.model.CoachingTone
import com.pace.reduction.domain.model.PacingStatus
import com.pace.reduction.domain.model.PlanSettings
import com.pace.reduction.domain.model.ReminderIntensity
import com.pace.reduction.widget.PaceWidget
import com.pace.reduction.widget.PaceWidgetReceiver
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
private data object TodayDestination : NavKey

@Serializable
private data object CoachDestination : NavKey

@Serializable
private data object VoiceCallDestination : NavKey

@Serializable
private data object PlanDestination : NavKey

@Serializable
private data object ToolkitDestination : NavKey

@Serializable
private data object ProgressDestination : NavKey

@Serializable
private data object SettingsDestination : NavKey

@Serializable
private data object HistoryDestination : NavKey

private data class NavigationItem(
    val key: NavKey,
    val label: Int,
    val icon: ImageVector,
)

@Composable
fun PaceApp(viewModel: PaceViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val loggedMessage = stringResource(R.string.cigarette_logged)
    val undoLabel = stringResource(R.string.undo)
    val undoneMessage = stringResource(R.string.log_undone)
    val planSavedMessage = stringResource(R.string.plan_saved)
    val checkInSavedMessage = stringResource(R.string.check_in_saved)
    val backupExportedMessage = stringResource(R.string.backup_exported)
    val backupImportedMessage = stringResource(R.string.backup_imported)
    val backupFailedMessage = stringResource(R.string.backup_failed)
    val coachSavedMessage = stringResource(R.string.coach_settings_saved)
    val historyUpdatedMessage = stringResource(R.string.history_updated)
    val historyRejectedMessage = stringResource(R.string.history_rejected)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is PaceEvent.CigaretteLogged -> {
                    val result = snackbarHostState.showSnackbar(
                        message = loggedMessage,
                        actionLabel = undoLabel,
                        duration = SnackbarDuration.Long,
                    )
                    if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                        viewModel.undoLog(event.id)
                    }
                }

                PaceEvent.LogUndone -> snackbarHostState.showSnackbar(undoneMessage)
                PaceEvent.PlanSaved -> snackbarHostState.showSnackbar(planSavedMessage)
                PaceEvent.CheckInSaved -> snackbarHostState.showSnackbar(checkInSavedMessage)
                is PaceEvent.PauseCompleted -> Unit
                PaceEvent.DataDeleted -> Unit
                PaceEvent.BackupExported -> snackbarHostState.showSnackbar(backupExportedMessage)
                PaceEvent.BackupImported -> snackbarHostState.showSnackbar(backupImportedMessage)
                PaceEvent.BackupFailed -> snackbarHostState.showSnackbar(backupFailedMessage)
                PaceEvent.CoachSettingsSaved -> snackbarHostState.showSnackbar(coachSavedMessage)
                is PaceEvent.ApiKeyVerified -> Unit
                PaceEvent.HistoryUpdated -> snackbarHostState.showSnackbar(historyUpdatedMessage)
                PaceEvent.HistoryRejected -> snackbarHostState.showSnackbar(historyRejectedMessage)
            }
        }
    }

    // The pedometer keeps counting whether or not the app is watching, so one reading each time
    // the app is resumed picks up everything walked since the last look.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner, uiState.settings.stepCountingEnabled) {
        if (!uiState.settings.stepCountingEnabled) return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) {
            viewModel.sampleSteps()
        }
    }

    when {
        uiState.loading -> LoadingScreen()
        !uiState.settings.onboardingCompleted -> GuidedOnboardingScreen(
            initial = uiState.settings,
            onFinish = viewModel::completeOnboarding,
        )

        else -> MainShell(
            uiState = uiState,
            snackbarHostState = snackbarHostState,
            onLog = viewModel::logCigarette,
            onSavePlan = viewModel::savePlan,
            viewModel = viewModel,
        )
    }
}

@Composable
private fun LoadingScreen() {
    val loadingDescription = stringResource(R.string.loading_pace)
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            modifier = Modifier
                .breathe(0.94f, 1.06f, 2_000)
                .semantics { contentDescription = loadingDescription },
        )
    }
}

/** Lifts the selected tab's icon. Split out so the animation state is not rebuilt per recomposition. */
@Composable
private fun Modifier.navSelectionScale(selected: Boolean): Modifier {
    val motion = LocalMotion.current
    val factor by animateFloatAsState(
        targetValue = if (selected && motion.enabled) 1.12f else 1f,
        animationSpec = motion.springy(),
        label = "navIcon",
    )
    return this.scale(factor)
}

@Composable
private fun MainShell(
    uiState: PaceUiState,
    snackbarHostState: SnackbarHostState,
    onLog: () -> Unit,
    onSavePlan: (PlanSettings) -> Unit,
    viewModel: PaceViewModel,
) {
    val activity = LocalActivity.current
    val initialDestination: NavKey = remember {
        val requested = activity?.intent?.getStringExtra(com.pace.reduction.MainActivity.EXTRA_DESTINATION)
        activity?.intent?.removeExtra(com.pace.reduction.MainActivity.EXTRA_DESTINATION)
        when (requested) {
            com.pace.reduction.MainActivity.DESTINATION_TOOLKIT -> ToolkitDestination
            com.pace.reduction.MainActivity.DESTINATION_COACH -> CoachDestination
            com.pace.reduction.MainActivity.DESTINATION_CALL -> VoiceCallDestination
            else -> TodayDestination
        }
    }
    val backStack = rememberNavBackStack(initialDestination)
    val navigationItems = listOf(
        NavigationItem(TodayDestination, R.string.nav_today, Icons.Outlined.Home),
        NavigationItem(CoachDestination, R.string.nav_coach, Icons.Outlined.Forum),
        NavigationItem(ToolkitDestination, R.string.nav_toolkit, Icons.Outlined.Psychology),
        NavigationItem(ProgressDestination, R.string.nav_progress, Icons.Outlined.BarChart),
    )
    val current = backStack.lastOrNull()
    val motion = LocalMotion.current
    val haptics = LocalHapticFeedback.current
    val hapticsEnabled = uiState.settings.hapticsEnabled

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar {
                navigationItems.forEach { item ->
                    val selected = current == item.key
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            if (!selected) {
                                if (hapticsEnabled) {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                                backStack.clear()
                                backStack.add(item.key)
                            }
                        },
                        icon = {
                            // The selected tab's icon lifts a fraction, which reads as depth on a
                            // bar where the pill indicator alone is easy to miss in peripheral vision.
                            Icon(
                                item.icon,
                                contentDescription = null,
                                modifier = Modifier.navSelectionScale(selected),
                            )
                        },
                        label = { Text(stringResource(item.label)) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavDisplay(
            backStack = backStack,
            // Consuming as well as applying matters: without it every inner top app bar reads the
            // status-bar inset a second time and the titles sit a bar's height too low.
            modifier = Modifier.padding(innerPadding).consumeWindowInsets(innerPadding),
            onBack = {
                if (backStack.size > 1) backStack.removeLastOrNull()
            },
            // The library default is a 700ms crossfade, which on a phone reads as lag rather than
            // grace. A short slide in the direction of travel says which way the stack moved.
            transitionSpec = {
                (
                    slideInHorizontally(motion.eased(300)) { width -> width / 5 } +
                        fadeIn(motion.eased(240))
                    ) togetherWith (
                    slideOutHorizontally(motion.eased(300)) { width -> -width / 12 } +
                        fadeOut(motion.eased(180))
                    )
            },
            popTransitionSpec = {
                (
                    slideInHorizontally(motion.eased(300)) { width -> -width / 12 } +
                        fadeIn(motion.eased(240))
                    ) togetherWith (
                    slideOutHorizontally(motion.eased(300)) { width -> width / 5 } +
                        fadeOut(motion.eased(180))
                    )
            },
            entryProvider = entryProvider {
                entry<TodayDestination> {
                    TodayScreen(
                        uiState = uiState,
                        onLog = onLog,
                        onOpenToolkit = {
                            backStack.clear()
                            backStack.add(ToolkitDestination)
                        },
                        onOpenCoach = {
                            backStack.clear()
                            backStack.add(CoachDestination)
                        },
                        onOpenPlan = { backStack.add(PlanDestination) },
                        onOpenSettings = { backStack.add(SettingsDestination) },
                    )
                }
                entry<CoachDestination> {
                    CoachScreen(
                        uiState = uiState,
                        viewModel = viewModel,
                        onOpenSettings = { backStack.add(SettingsDestination) },
                        onStartCall = { backStack.add(VoiceCallDestination) },
                    )
                }
                entry<VoiceCallDestination> {
                    VoiceCallScreen(
                        viewModel = viewModel,
                        onBack = { backStack.removeLastOrNull() },
                    )
                }
                entry<PlanDestination> {
                    PlanScreen(uiState, onSavePlan, onBack = { backStack.removeLastOrNull() })
                }
                entry<ToolkitDestination> {
                    EnhancedToolkitScreen(
                        uiState = uiState,
                        viewModel = viewModel,
                        onOpenSettings = { backStack.add(SettingsDestination) },
                    )
                }
                entry<ProgressDestination> {
                    ProgressScreen(
                        uiState = uiState,
                        onOpenSettings = { backStack.add(SettingsDestination) },
                        onToggleSteps = viewModel::setStepCounting,
                    )
                }
                entry<SettingsDestination> {
                    SettingsScreen(
                        uiState = uiState,
                        viewModel = viewModel,
                        onSave = onSavePlan,
                        onExport = viewModel::exportData,
                        onImport = viewModel::importData,
                        onDeleteAll = viewModel::deleteAllData,
                        onOpenHistory = { backStack.add(HistoryDestination) },
                        onBack = { backStack.removeLastOrNull() },
                    )
                }
                entry<HistoryDestination> {
                    HistoryEditorScreen(
                        uiState = uiState,
                        viewModel = viewModel,
                        onBack = { backStack.removeLastOrNull() },
                    )
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TodayScreen(
    uiState: PaceUiState,
    onLog: () -> Unit,
    onOpenToolkit: () -> Unit,
    onOpenCoach: () -> Unit,
    onOpenPlan: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val today = requireNotNull(uiState.today)
    val quit = uiState.quit
    val resting = today.status is PacingStatus.Rest
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val locale = LocalConfiguration.current.locales[0]
    var widgetInstalled by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(Unit) {
        widgetInstalled = GlanceAppWidgetManager(context)
            .getGlanceIds(PaceWidget::class.java)
            .isNotEmpty()
    }

    val coachingMessage = when (uiState.settings.coachingTone) {
        CoachingTone.SUPPORTIVE -> stringResource(R.string.coaching_supportive)
        CoachingTone.DIRECT -> stringResource(R.string.coaching_direct)
        CoachingTone.TOUGH -> stringResource(R.string.coaching_tough)
    }

    PaceScreen(
        title = stringResource(R.string.today_title),
        subtitle = today.localDate.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)),
        actions = {
            IconButton(onClick = onOpenPlan) {
                Icon(Icons.Outlined.CalendarMonth, contentDescription = stringResource(R.string.plan_title))
            }
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.settings_title))
            }
        },
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 28.dp),
    ) {
        item {
            Box(Modifier.entrance(0)) {
                if (resting) RestHero() else PacingHero(uiState)
            }
        }
        if (!resting) item {
            Column(
                modifier = Modifier.entrance(1),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                LogButton(onLog = onLog, hapticsEnabled = uiState.settings.hapticsEnabled)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onOpenCoach, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.Forum, contentDescription = null)
                        Spacer(Modifier.size(6.dp))
                        Text(stringResource(R.string.have_craving))
                    }
                    OutlinedButton(onClick = onOpenToolkit, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.Timer, contentDescription = null)
                        Spacer(Modifier.size(6.dp))
                        Text(stringResource(R.string.pause_five))
                    }
                }
            }
        }
        if (quit != null && !resting) {
            item {
                val stats = buildList {
                    add(formatSmokeFree(quit.smokeFreeDuration) to stringResource(R.string.quit_smoke_free))
                    add(quit.cigarettesAvoided.toString() to stringResource(R.string.quit_avoided))
                    add(formatLifeRegained(quit.minutesOfLifeRegained) to stringResource(R.string.quit_life))
                    if (uiState.settings.pricePerPack > 0) {
                        add(
                            String.format(locale, "%.0f", quit.moneySaved) to
                                stringResource(R.string.quit_saved),
                        )
                    }
                    if (uiState.steps.enabled && uiState.steps.permissionGranted) {
                        add(
                            formatStepsCompact(uiState.steps.todaySteps, locale) to
                                stringResource(R.string.steps_today),
                        )
                    }
                }
                StatGrid(stats, modifier = Modifier.entrance(2))
            }
            item {
                NextMilestoneCard(quit, modifier = Modifier.entrance(3))
            }
        }
        if (!resting) item {
            SectionCard(modifier = Modifier.entrance(4)) {
                Text(coachingMessage, style = MaterialTheme.typography.titleMedium)
                if (uiState.settings.personalReason.isNotBlank()) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                    Text(stringResource(R.string.your_reason), style = MaterialTheme.typography.labelLarge)
                    Text(uiState.settings.personalReason, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        if (widgetInstalled == false) {
            item {
                SectionCard {
                    Text(stringResource(R.string.add_widget_title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.add_widget_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                GlanceAppWidgetManager(context).requestPinGlanceAppWidget(
                                    receiver = PaceWidgetReceiver::class.java,
                                    preview = PaceWidget(),
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.add_widget_action))
                    }
                }
            }
        }
    }
}

/** Sleep hours deliberately contain no counter, timer, smoke-free duration, or coaching cue. */
@Composable
private fun RestHero() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(
                Icons.Outlined.Bedtime,
                contentDescription = null,
                modifier = Modifier.size(44.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(stringResource(R.string.rest_mode_title), style = MaterialTheme.typography.headlineSmall)
            Text(
                stringResource(R.string.rest_mode_body),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

/**
 * The one destructive-ish button on the screen, so it answers the finger before the database does.
 *
 * It shrinks under the press, thumps once, and flips to a plain acknowledgement for a beat. The
 * acknowledgement is the point: logging honestly is the behaviour this app most needs to stay
 * easy, so the moment after a tap has to feel like being met rather than being marked down.
 */
@Composable
private fun LogButton(onLog: () -> Unit, hapticsEnabled: Boolean) {
    val motion = LocalMotion.current
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    var acknowledged by remember { mutableStateOf(false) }

    LaunchedEffect(acknowledged) {
        if (acknowledged) {
            kotlinx.coroutines.delay(1_400)
            acknowledged = false
        }
    }

    Button(
        onClick = {
            if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            acknowledged = true
            onLog()
        },
        interactionSource = interaction,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .pressScale(interaction),
    ) {
        AnimatedContent(
            targetState = acknowledged,
            transitionSpec = {
                (scaleIn(motion.springy(), initialScale = 0.8f) + fadeIn(motion.eased(200))) togetherWith
                    fadeOut(motion.eased(160))
            },
            label = "logButton",
        ) { done ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (done) Icons.Outlined.CheckCircle else Icons.Outlined.AddCircle,
                    contentDescription = null,
                )
                Spacer(Modifier.size(10.dp))
                Text(stringResource(if (done) R.string.cigarette_logged else R.string.log_cigarette))
            }
        }
    }
}

/** Ring + status + next-window countdown, the first thing you see each day. */
@Composable
private fun PacingHero(uiState: PaceUiState) {
    val today = requireNotNull(uiState.today)
    val motion = LocalMotion.current
    val statusTitle: String
    val statusDetail: String
    val accent: Color
    /** How far through the current wait, or null when nothing is being waited for. */
    val windowProgress: Float?
    val windowOpen: Boolean

    when (val status = today.status) {
        is PacingStatus.Spacing -> {
            statusTitle = stringResource(R.string.status_spacing)
            statusDetail = stringResource(
                R.string.status_spacing_detail,
                durationText(PacingCalculator.remaining(uiState.now, status.earliestWindow)),
                status.earliestWindow.format(DateTimeFormatter.ofPattern("HH:mm")),
            )
            accent = MaterialTheme.colorScheme.primary
            windowProgress = waitProgress(
                now = uiState.now,
                target = status.earliestWindow.toInstant(),
                spanMinutes = uiState.spacing?.effectiveMinutes ?: uiState.settings.minimumGapMinutes,
            )
            windowOpen = false
        }
        is PacingStatus.WindowMet -> {
            statusTitle = stringResource(R.string.status_window_met)
            statusDetail = stringResource(R.string.status_window_met_detail)
            accent = MaterialTheme.colorScheme.primary
            windowProgress = null
            windowOpen = true
        }
        is PacingStatus.MorningHold -> {
            statusTitle = stringResource(R.string.status_morning_hold)
            statusDetail = stringResource(
                R.string.status_until,
                status.until.format(DateTimeFormatter.ofPattern("HH:mm")),
            )
            accent = MaterialTheme.colorScheme.secondary
            windowProgress = waitProgress(
                now = uiState.now,
                target = status.until.toInstant(),
                spanMinutes = uiState.settings.morningHoldMinutes,
            )
            windowOpen = false
        }
        is PacingStatus.Rest -> {
            statusTitle = stringResource(R.string.status_rest)
            statusDetail = stringResource(
                R.string.status_next_wake,
                status.nextWake.format(DateTimeFormatter.ofPattern("HH:mm")),
            )
            accent = MaterialTheme.colorScheme.secondary
            windowProgress = null
            windowOpen = false
        }
        PacingStatus.CeilingReached -> {
            statusTitle = stringResource(R.string.status_ceiling)
            statusDetail = stringResource(R.string.status_ceiling_detail)
            accent = MaterialTheme.colorScheme.secondary
            windowProgress = null
            windowOpen = false
        }
        PacingStatus.Recovery -> {
            statusTitle = stringResource(R.string.status_recovery)
            statusDetail = stringResource(R.string.status_recovery_detail)
            accent = MaterialTheme.colorScheme.tertiary
            windowProgress = null
            windowOpen = false
        }
    }

    // The card itself carries the accent as a faint wash, so the hero belongs to the current state
    // rather than sitting on neutral card stock like every other section.
    val wash = accent.copy(alpha = if (isSystemInDarkTheme()) 0.16f else 0.10f)
    val animatedWash by animateColorAsState(wash, motion.eased(500), label = "heroWash")

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Box(
            modifier = Modifier.background(
                Brush.verticalGradient(listOf(animatedWash, Color.Transparent)),
            ),
        ) {
            Column(
                modifier = Modifier.padding(vertical = 24.dp, horizontal = 20.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                ProgressRing(
                    count = today.count,
                    ceiling = today.ceiling,
                    accent = accent,
                    windowProgress = windowProgress,
                    glow = windowOpen,
                    modifier = if (windowOpen) Modifier.breathe(0.985f, 1.015f) else Modifier,
                )
                StatusLine(title = statusTitle, accent = accent, live = windowProgress != null)
                AnimatedContent(
                    targetState = statusDetail,
                    transitionSpec = { fadeIn(motion.eased(220)) togetherWith fadeOut(motion.eased(180)) },
                    label = "statusDetail",
                ) { detail ->
                    Text(
                        detail,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                }
                Text(
                    if (today.lastLogAt == null) {
                        stringResource(R.string.no_logs_today)
                    } else {
                        stringResource(
                            R.string.last_logged_at,
                            today.lastLogAt.atZone(java.time.ZoneId.systemDefault())
                                .format(DateTimeFormatter.ofPattern("HH:mm")),
                        )
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** The status headline, with a dot that keeps a slow beat while a wait is actually running. */
@Composable
private fun StatusLine(title: String, accent: Color, live: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(9.dp)
                .then(if (live) Modifier.pulseAlpha(0.3f, 1f, 1_800) else Modifier)
                .background(accent, CircleShape),
        )
        Spacer(Modifier.size(8.dp))
        Text(title, style = MaterialTheme.typography.titleLarge)
    }
}

/**
 * Fraction of a wait already served, from the time still left and how long the wait was.
 *
 * Derived rather than stored because the start of a wait is not always a recorded event — a
 * morning hold begins at whatever wake time the plan says, and back-solving from the deadline
 * gives the same answer without another field to keep honest.
 */
private fun waitProgress(now: java.time.Instant, target: java.time.Instant, spanMinutes: Int): Float? {
    if (spanMinutes <= 0) return null
    val remaining = Duration.between(now, target).toMillis()
    if (remaining <= 0L) return 1f
    val span = spanMinutes * 60_000f
    return ((span - remaining) / span).coerceIn(0f, 1f)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlanScreen(uiState: PaceUiState, onSavePlan: (PlanSettings) -> Unit, onBack: () -> Unit) {
    val settings = uiState.settings
    var baseline by rememberSaveable(settings.baselinePerDay) { mutableStateOf(settings.baselinePerDay.toString()) }
    var ceiling by rememberSaveable(settings.dailyCeiling) { mutableStateOf(settings.dailyCeiling.toString()) }
    var gap by rememberSaveable(settings.minimumGapMinutes) { mutableStateOf(settings.minimumGapMinutes.toString()) }
    var wake by rememberSaveable(settings.wakeMinutes) { mutableStateOf(formatMinutes(settings.wakeMinutes)) }
    var sleep by rememberSaveable(settings.sleepMinutes) { mutableStateOf(formatMinutes(settings.sleepMinutes)) }
    var morningHold by rememberSaveable(settings.morningHoldMinutes) { mutableStateOf(settings.morningHoldMinutes.toString()) }
    var weekendWakeEnabled by rememberSaveable(settings.weekendWakeEnabled) { mutableStateOf(settings.weekendWakeEnabled) }
    var weekendWake by rememberSaveable(settings.weekendWakeMinutes) { mutableStateOf(formatMinutes(settings.weekendWakeMinutes)) }
    var reductionStep by rememberSaveable(settings.reductionStep) { mutableStateOf(settings.reductionStep.toString()) }
    var reviewInterval by rememberSaveable(settings.reviewIntervalDays) { mutableStateOf(settings.reviewIntervalDays.toString()) }
    var pricePerPack by rememberSaveable(settings.pricePerPack) { mutableStateOf(settings.pricePerPack.takeIf { it > 0 }?.toString() ?: "") }
    var cigarettesPerPack by rememberSaveable(settings.cigarettesPerPack) { mutableStateOf(settings.cigarettesPerPack.toString()) }
    var currencyCode by rememberSaveable(settings.currencyCode) { mutableStateOf(settings.currencyCode) }
    var rewardName by rememberSaveable(settings.rewardName) { mutableStateOf(settings.rewardName) }
    var rewardTarget by rememberSaveable(settings.rewardTarget) { mutableStateOf(settings.rewardTarget.takeIf { it > 0 }?.toString() ?: "") }
    var personalReason by rememberSaveable(settings.personalReason) { mutableStateOf(settings.personalReason) }
    var flexibleDay by rememberSaveable(settings.flexibleDay) { mutableStateOf(settings.flexibleDay) }
    var tone by rememberSaveable(settings.coachingTone) { mutableStateOf(settings.coachingTone) }
    var reminder by rememberSaveable(settings.reminderIntensity) { mutableStateOf(settings.reminderIntensity) }
    var hapticsEnabled by rememberSaveable(settings.hapticsEnabled) { mutableStateOf(settings.hapticsEnabled) }
    var quitMode by rememberSaveable(settings.quitMode) { mutableStateOf(settings.quitMode) }
    var quitDate by rememberSaveable(settings.quitDate) { mutableStateOf(settings.quitDate?.toString() ?: "") }
    var adaptiveSpacing by rememberSaveable(settings.adaptiveSpacingEnabled) { mutableStateOf(settings.adaptiveSpacingEnabled) }
    var adaptiveStep by rememberSaveable(settings.adaptiveSpacingStepMinutes) { mutableStateOf(settings.adaptiveSpacingStepMinutes.toString()) }
    var adaptiveInterval by rememberSaveable(settings.adaptiveSpacingIntervalDays) { mutableStateOf(settings.adaptiveSpacingIntervalDays.toString()) }
    var adaptiveMax by rememberSaveable(settings.adaptiveSpacingMaxMinutes) { mutableStateOf(settings.adaptiveSpacingMaxMinutes.toString()) }
    var highUrgeEnabled by rememberSaveable(settings.highUrgeWindowEnabled) { mutableStateOf(settings.highUrgeWindowEnabled) }
    var highUrgeStart by rememberSaveable(settings.highUrgeStartMinutes) { mutableStateOf(formatMinutes(settings.highUrgeStartMinutes)) }
    var highUrgeEnd by rememberSaveable(settings.highUrgeEndMinutes) { mutableStateOf(formatMinutes(settings.highUrgeEndMinutes)) }
    var height by rememberSaveable(settings.heightCentimetres) {
        mutableStateOf(settings.heightCentimetres.takeIf { it > 0 }?.toString() ?: "")
    }

    val newCeiling = ceiling.toIntOrNull()
    val parsedWake = parseTime(wake)
    val parsedSleep = parseTime(sleep)
    val parsedWeekendWake = parseTime(weekendWake)
    val parsedQuitDate = runCatching { LocalDate.parse(quitDate) }.getOrNull()
    val valid = baseline.toIntOrNull() in 1..100 && newCeiling in 0..100 &&
        gap.toIntOrNull() in 15..360 && morningHold.toIntOrNull() in 0..240 &&
        parsedWake != null && parsedSleep != null && parsedWake != parsedSleep &&
        (!weekendWakeEnabled || parsedWeekendWake != null) && reductionStep.toIntOrNull() in 1..5 &&
        reviewInterval.toIntOrNull() in 7..28 && cigarettesPerPack.toIntOrNull() in 1..100 &&
        (pricePerPack.toDoubleOrNull() ?: 0.0) >= 0.0 && (rewardTarget.toDoubleOrNull() ?: 0.0) >= 0.0 &&
        currencyCode.length == 3 && (!quitMode || quitDate.isBlank() || parsedQuitDate != null) &&
        (
            !adaptiveSpacing || (
                adaptiveStep.toIntOrNull() in 5..60 &&
                    adaptiveInterval.toIntOrNull() in 1..30 &&
                    adaptiveMax.toIntOrNull() in 30..720
                )
            ) &&
        (!highUrgeEnabled || (parseTime(highUrgeStart) != null && parseTime(highUrgeEnd) != null)) &&
        (height.isBlank() || height.toIntOrNull() in 100..250)

    PaceScreen(
        title = stringResource(R.string.plan_title),
        onBack = onBack,
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        // Applying the plan is the point of the screen, so the button that does it no longer sits
        // below fifteen fields of scroll where it cannot be found without hunting.
        bottomBar = {
            StickyActionBar {
                Button(
                    onClick = {
                        onSavePlan(
                            settings.copy(
                                baselinePerDay = baseline.toInt(),
                                dailyCeiling = ceiling.toInt(),
                                minimumGapMinutes = gap.toInt(),
                                wakeMinutes = requireNotNull(parsedWake),
                                sleepMinutes = requireNotNull(parsedSleep),
                                morningHoldMinutes = morningHold.toInt(),
                                weekendWakeEnabled = weekendWakeEnabled,
                                weekendWakeMinutes = parsedWeekendWake ?: settings.weekendWakeMinutes,
                                flexibleDay = flexibleDay,
                                reductionStep = reductionStep.toInt(),
                                reviewIntervalDays = reviewInterval.toInt(),
                                pricePerPack = pricePerPack.toDoubleOrNull() ?: 0.0,
                                cigarettesPerPack = cigarettesPerPack.toInt(),
                                currencyCode = currencyCode,
                                coachingTone = tone,
                                reminderIntensity = reminder,
                                hapticsEnabled = hapticsEnabled,
                                personalReason = personalReason,
                                rewardName = rewardName,
                                rewardTarget = rewardTarget.toDoubleOrNull() ?: 0.0,
                                quitMode = quitMode,
                                quitDate = parsedQuitDate,
                                adaptiveSpacingEnabled = adaptiveSpacing,
                                adaptiveSpacingStepMinutes = adaptiveStep.toIntOrNull()
                                    ?: settings.adaptiveSpacingStepMinutes,
                                adaptiveSpacingIntervalDays = adaptiveInterval.toIntOrNull()
                                    ?: settings.adaptiveSpacingIntervalDays,
                                adaptiveSpacingMaxMinutes = adaptiveMax.toIntOrNull()
                                    ?: settings.adaptiveSpacingMaxMinutes,
                                highUrgeWindowEnabled = highUrgeEnabled,
                                highUrgeStartMinutes = parseTime(highUrgeStart) ?: settings.highUrgeStartMinutes,
                                highUrgeEndMinutes = parseTime(highUrgeEnd) ?: settings.highUrgeEndMinutes,
                                heightCentimetres = height.toIntOrNull()?.takeIf { it in 100..250 } ?: 0,
                            ),
                        )
                    },
                    enabled = valid,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Text(stringResource(R.string.preview_and_apply))
                }
            }
        },
    ) {
        item { LeadParagraph(stringResource(R.string.plan_intro)) }
        item { SectionHeader(stringResource(R.string.plan_section_limits)) }
        item { PlanNumberField(R.string.baseline_label, baseline, { baseline = it }, 1..100) }
        item { PlanNumberField(R.string.ceiling_label, ceiling, { ceiling = it }, 0..100) }
        if (newCeiling != null && newCeiling < (uiState.today?.count ?: 0)) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Text(
                        stringResource(R.string.tomorrow_effective_warning),
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
        item { PlanNumberField(R.string.spacing_label, gap, { gap = it }, 15..360) }
        item {
            SectionCard {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.adaptive_spacing_title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            stringResource(
                                R.string.adaptive_spacing_body,
                                adaptiveInterval.toIntOrNull() ?: settings.adaptiveSpacingIntervalDays,
                                adaptiveStep.toIntOrNull() ?: settings.adaptiveSpacingStepMinutes,
                                adaptiveMax.toIntOrNull() ?: settings.adaptiveSpacingMaxMinutes,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = adaptiveSpacing, onCheckedChange = { adaptiveSpacing = it })
                }
                uiState.spacing?.takeIf { adaptiveSpacing }?.let { spacing ->
                    Text(
                        text = if (spacing.atMaximum) {
                            stringResource(R.string.adaptive_spacing_max_reached, spacing.effectiveMinutes)
                        } else {
                            stringResource(
                                R.string.adaptive_spacing_now,
                                spacing.effectiveMinutes,
                                spacing.steadyDays,
                                spacing.steadyDaysNeeded,
                            )
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                if (adaptiveSpacing) {
                    PlanNumberField(R.string.adaptive_spacing_step, adaptiveStep, { adaptiveStep = it }, 5..60)
                    PlanNumberField(R.string.adaptive_spacing_interval, adaptiveInterval, { adaptiveInterval = it }, 1..30)
                    PlanNumberField(R.string.adaptive_spacing_max, adaptiveMax, { adaptiveMax = it }, 30..720)
                }
            }
        }
        item { SectionHeader(stringResource(R.string.plan_section_day)) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ClockField(R.string.wake_label, wake, { wake = it }, Modifier.weight(1f))
                ClockField(R.string.sleep_label, sleep, { sleep = it }, Modifier.weight(1f))
            }
        }
        item {
            SectionCard {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.high_urge_title), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.high_urge_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = highUrgeEnabled, onCheckedChange = { highUrgeEnabled = it })
                }
                if (highUrgeEnabled) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ClockField(R.string.high_urge_from, highUrgeStart, { highUrgeStart = it }, Modifier.weight(1f))
                        ClockField(R.string.high_urge_to, highUrgeEnd, { highUrgeEnd = it }, Modifier.weight(1f))
                    }
                }
            }
        }
        item { PlanNumberField(R.string.morning_hold_label, morningHold, { morningHold = it }, 0..240) }
        item {
            // Only ever used to turn steps into a distance, which is why it lives here rather than
            // in a "profile" the app otherwise has no use for.
            PlanNumberField(R.string.height_label, height, { height = it }, 100..250)
        }
        item {
            SectionCard {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.quit_mode_title), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.quit_mode_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = quitMode, onCheckedChange = { quitMode = it })
                }
                if (quitMode) {
                    OutlinedTextField(
                        value = quitDate,
                        onValueChange = { quitDate = it.take(10) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.quit_date_label)) },
                        isError = quitDate.isNotBlank() && parsedQuitDate == null,
                        singleLine = true,
                    )
                }
            }
        }
        item {
            SectionCard {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.weekend_wake), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.weekend_wake_support),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = weekendWakeEnabled, onCheckedChange = { weekendWakeEnabled = it })
                }
                if (weekendWakeEnabled) {
                    ClockField(R.string.weekend_wake_time, weekendWake, { weekendWake = it }, Modifier.fillMaxWidth())
                }
            }
        }
        item {
            SectionCard {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.flexible_day), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.flexible_day_support),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = flexibleDay, onCheckedChange = { flexibleDay = it })
                }
            }
        }
        item { SectionHeader(stringResource(R.string.plan_section_reduction)) }
        item {
            SectionCard {
                Text(stringResource(R.string.reduction_review_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.reduction_review_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                PlanNumberField(R.string.reduction_step_label, reductionStep, { reductionStep = it }, 1..5)
                PlanNumberField(R.string.review_interval_label, reviewInterval, { reviewInterval = it }, 7..28)
            }
        }
        item { SectionHeader(stringResource(R.string.plan_section_money)) }
        item {
            SectionCard {
                Text(stringResource(R.string.savings_settings_title), style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = pricePerPack,
                    onValueChange = { entered -> if (entered.length <= 8 && entered.all { it.isDigit() || it == '.' }) pricePerPack = entered },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.price_per_pack)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                PlanNumberField(R.string.cigarettes_per_pack, cigarettesPerPack, { cigarettesPerPack = it }, 1..100)
                OutlinedTextField(
                    value = currencyCode,
                    onValueChange = { currencyCode = it.filter(Char::isLetter).take(3).uppercase() },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.currency_code)) },
                    singleLine = true,
                )
            }
        }
        item { SectionHeader(stringResource(R.string.plan_section_motivation)) }
        item {
            OutlinedTextField(
                value = personalReason,
                onValueChange = { personalReason = it.take(500) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.personal_reason_label)) },
                minLines = 2,
            )
        }
        item {
            SectionCard {
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
        item {
            SectionCard {
                Text(stringResource(R.string.reward_title), style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = rewardName,
                    onValueChange = { rewardName = it.take(100) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.reward_name)) },
                )
                OutlinedTextField(
                    value = rewardTarget,
                    onValueChange = { entered -> if (entered.length <= 10 && entered.all { it.isDigit() || it == '.' }) rewardTarget = entered },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.reward_target)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
        }
        item { SectionHeader(stringResource(R.string.plan_section_reminders)) }
        item {
            SectionCard {
                // No inner title here: the section header directly above already says "Reminders".
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.haptics), modifier = Modifier.weight(1f))
                    Switch(checked = hapticsEnabled, onCheckedChange = { hapticsEnabled = it })
                }
            }
        }
    }
}

@Composable
private fun ProgressScreen(
    uiState: PaceUiState,
    onOpenSettings: () -> Unit,
    onToggleSteps: (Boolean) -> Unit,
) {
    val metrics = requireNotNull(uiState.progress)
    val quit = uiState.quit
    val locale = LocalConfiguration.current.locales[0]
    var rangeDays by rememberSaveable { mutableIntStateOf(7) }
    val visibleDays = metrics.days.takeLast(rangeDays)
    val max = maxOf(
        visibleDays.maxOfOrNull { it.count } ?: 1,
        visibleDays.maxOfOrNull { it.ceiling ?: 0 } ?: 1,
        1,
    )
    val triggerSample = uiState.urgeSessions.filter { it.triggerTags.isNotEmpty() }
    val topTrigger = triggerSample.flatMap { it.triggerTags }
        .groupingBy { it }
        .eachCount()
        .maxByOrNull { it.value }
    val suggestedCeiling = ReductionPlanner.suggestedCeiling(
        currentCeiling = uiState.settings.dailyCeiling,
        step = uiState.settings.reductionStep,
        reviewIntervalDays = uiState.settings.reviewIntervalDays,
        completedDays = metrics.days,
    )

    PaceScreen(
        title = stringResource(R.string.progress_title),
        actions = {
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.settings_title))
            }
        },
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { LeadParagraph(stringResource(R.string.progress_intro)) }
        if (quit != null) {
            item {
                StatGrid(
                    listOf(
                        quit.zeroDayStreak.toString() to stringResource(R.string.quit_streak),
                        metrics.avoidedCigarettes.toString() to stringResource(R.string.estimated_avoided),
                        String.format(
                            locale,
                            "%.0f %s",
                            metrics.estimatedSavings,
                            uiState.settings.currencyCode,
                        ) to stringResource(R.string.estimated_savings),
                    ),
                )
            }
        }
        item {
            SectionCard {
                Text(stringResource(R.string.progress_history), style = MaterialTheme.typography.titleLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = rangeDays == 7, onClick = { rangeDays = 7 }, label = { Text(stringResource(R.string.seven_days)) })
                    FilterChip(selected = rangeDays == 30, onClick = { rangeDays = 30 }, label = { Text(stringResource(R.string.thirty_days)) })
                }
                Spacer(Modifier.height(6.dp))
                visibleDays.forEachIndexed { index, day ->
                    val ceiling = day.ceiling ?: uiState.settings.dailyCeiling
                    DayBar(
                        label = day.date.dayOfWeek.name.take(3),
                        value = if (day.recorded) {
                            stringResource(R.string.day_count_accessible, day.count, ceiling)
                        } else {
                            stringResource(R.string.day_unknown)
                        },
                        fraction = if (day.recorded) day.count.toFloat() / max.toFloat() else 0f,
                        overCeiling = day.recorded && ceiling > 0 && day.count > ceiling,
                        index = index,
                    )
                }
            }
        }
        item {
            StepSection(
                steps = uiState.steps,
                rangeDays = rangeDays,
                onToggle = onToggleSteps,
            )
        }
        if (quit != null) {
            item {
                SectionCard {
                    Text(stringResource(R.string.quit_health_title), style = MaterialTheme.typography.titleLarge)
                    Text(
                        stringResource(R.string.quit_best_streak, quit.bestZeroDayStreak),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    RecoveryTimeline(quit)
                }
            }
        }
        item {
            SectionCard {
                Text(stringResource(R.string.progress_evidence), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.average_seven, metrics.sevenDayAverage ?: 0.0))
                Text(stringResource(R.string.longest_gap, metrics.longestGapMinutes))
                Text(stringResource(R.string.best_morning_hold, metrics.bestMorningHoldMinutes))
                Text(stringResource(R.string.steady_days, metrics.steadyDays7, metrics.steadyDays30))
                Text(stringResource(R.string.pauses_recorded, metrics.pausesCompleted))
                Text(
                    stringResource(R.string.delay_is_win),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (uiState.settings.rewardTarget > 0) {
            item {
                SectionCard {
                    Text(stringResource(R.string.reward_progress_title), style = MaterialTheme.typography.titleLarge)
                    LinearProgressIndicator(progress = { metrics.rewardProgress.toFloat() }, modifier = Modifier.fillMaxWidth())
                    Text(stringResource(R.string.reward_progress_value, (metrics.rewardProgress * 100).toInt(), uiState.settings.rewardName.ifBlank { stringResource(R.string.your_reward) }))
                }
            }
        }
        if (topTrigger != null) {
            item {
                SectionCard {
                    Text(stringResource(R.string.pattern_reflection), style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.trigger_reflection, topTrigger.key.replace('_', ' '), topTrigger.value, triggerSample.size))
                }
            }
        }
        if (suggestedCeiling != null) {
            item {
                SectionCard {
                    Text(stringResource(R.string.review_suggestion_title), style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.review_suggestion_body, suggestedCeiling))
                }
            }
        }
        item {
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.badges_title),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        stringResource(R.string.badges_earned, uiState.achievements.size, BadgeCatalogue.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(4.dp))
                BadgeFamilyList(uiState.achievements)
            }
        }
    }
}

/**
 * One day of history.
 *
 * The bars grow from nothing as the list arrives, staggered by row, so a week of history reads
 * left-to-right like a chart being drawn rather than appearing pre-drawn — and a day that went
 * over its ceiling recolours instead of needing a legend.
 */
@Composable
private fun DayBar(
    label: String,
    value: String,
    fraction: Float,
    overCeiling: Boolean,
    index: Int,
) {
    val motion = LocalMotion.current
    // Saved, not remembered: these rows live in a lazy list, and a chart that redraws itself every
    // time it scrolls back into view stops reading as a chart.
    var shown by rememberSaveable { mutableStateOf(false) }
    val animated by animateFloatAsState(
        targetValue = if (shown) fraction.coerceIn(0f, 1f) else 0f,
        animationSpec = androidx.compose.animation.core.tween(
            durationMillis = motion.duration(520),
            delayMillis = motion.duration((index * 40).coerceAtMost(320)),
        ),
        label = "dayBar",
    )
    LaunchedEffect(Unit) { shown = true }

    Column(modifier = Modifier.padding(vertical = 5.dp)) {
        Row {
            Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
            Text(value, style = MaterialTheme.typography.labelMedium)
        }
        LinearProgressIndicator(
            progress = { animated },
            modifier = Modifier.fillMaxWidth().height(7.dp),
            color = if (overCeiling) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.primary
            },
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }
}

@Composable
internal fun SectionCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}

@Composable
private fun PlanNumberField(
    label: Int,
    value: String,
    onValueChange: (String) -> Unit,
    range: IntRange,
) {
    val parsed = value.toIntOrNull()
    OutlinedTextField(
        value = value,
        onValueChange = { entered ->
            if (entered.length <= 3 && entered.all(Char::isDigit)) onValueChange(entered)
        },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(label)) },
        isError = parsed != null && parsed !in range,
        supportingText = { Text(stringResource(R.string.allowed_range, range.first, range.last)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
    )
}

private fun parseTime(value: String): Int? = runCatching {
    val time = LocalTime.parse(value, DateTimeFormatter.ofPattern("HH:mm"))
    time.hour * 60 + time.minute
}.getOrNull()

private fun formatMinutes(totalMinutes: Int): String = "%02d:%02d".format(
    totalMinutes.coerceIn(0, 1439) / 60,
    totalMinutes.coerceIn(0, 1439) % 60,
)

private fun durationText(duration: Duration): String {
    val totalMinutes = duration.toMinutes().coerceAtLeast(0)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}
