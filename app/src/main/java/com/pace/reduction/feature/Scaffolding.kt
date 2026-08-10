package com.pace.reduction.feature

import android.text.format.DateFormat
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.animation.animateColorAsState
import androidx.compose.runtime.derivedStateOf
import com.pace.reduction.R
import com.pace.reduction.core.designsystem.LocalMotion
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * The chrome every screen shares.
 *
 * Before this existed each screen invented its own heading — a `TopAppBar` here, a bare
 * `displaySmall` there, a `headlineMedium` somewhere else — so moving between tabs shifted the
 * title's size, weight and position and the app read as several apps stapled together. One shell
 * means one answer to "where am I and how do I get back".
 *
 * It deliberately does **not** wrap a [androidx.compose.material3.Scaffold]. The shell around the
 * whole app already pads for the status bar and the navigation bar, so a nested Scaffold would
 * apply both a second time; the app bar is given empty window insets for the same reason.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PaceScreen(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable (() -> Unit)? = null,
    listState: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(14.dp),
    content: LazyListScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxSize()) {
        PaceTopBar(title = title, subtitle = subtitle, onBack = onBack, listState = listState, actions = actions)
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = contentPadding,
            verticalArrangement = verticalArrangement,
            content = content,
        )
        bottomBar?.invoke()
    }
}

/**
 * The bar itself, also usable on screens whose body is not a list (the coach's chat column).
 *
 * When [listState] is supplied the bar tints as soon as anything has scrolled underneath it.
 * Material's own scroll behaviour needs a nested-scroll connection threaded through every screen;
 * asking the list whether it can scroll back is the same signal for none of the plumbing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PaceTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    listState: LazyListState? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val motion = LocalMotion.current
    val lifted by remember(listState) {
        derivedStateOf { listState?.canScrollBackward == true }
    }
    val container by animateColorAsState(
        targetValue = if (lifted) {
            MaterialTheme.colorScheme.surfaceContainerHighest
        } else {
            MaterialTheme.colorScheme.background
        },
        animationSpec = motion.eased(220),
        label = "topBarLift",
    )

    TopAppBar(
        modifier = modifier,
        title = {
            Column {
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                    )
                }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = container),
        // The app shell has already padded for the status bar; taking the inset again would sink
        // the title into the middle of the screen.
        windowInsets = WindowInsets(0, 0, 0, 0),
    )
}

/**
 * The one-line explanation a screen opens with.
 *
 * These sentences used to ride in the app bar as subtitles, where they ran the full width, crowded
 * the actions and read louder than the title they were meant to support. A bar subtitle only suits
 * something short and factual, like a date.
 */
@Composable
internal fun LeadParagraph(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * A quiet label that starts a group of related controls.
 *
 * The plan screen used to be twenty unlabelled fields in a column, which gave the user no way to
 * tell where one decision ended and the next began.
 */
@Composable
internal fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier.padding(top = 8.dp, start = 4.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}

/**
 * An action bar pinned under the content, for screens where the commit button used to live at the
 * bottom of a very long scroll.
 */
@Composable
internal fun StickyActionBar(content: @Composable () -> Unit) {
    Surface(tonalElevation = 3.dp, shadowElevation = 8.dp) {
        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
            content()
        }
    }
}

/**
 * A time of day, chosen from a clock rather than typed.
 *
 * Both the plan screen and onboarding used free-text `HH:mm` boxes that sat in a red error state
 * until the user happened to type the separator the parser wanted — and onboarding's was the
 * second question the app ever asks. A wake time is a time, and the platform already has a control
 * for picking one. The field is read-only and opens the dialog on tap; the stored value stays
 * `HH:mm` so the existing parsers are untouched.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ClockField(
    label: Int,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var picking by rememberSaveable { mutableStateOf(false) }
    val minutes = parseClock(value)
    val interaction = remember { MutableInteractionSource() }

    // A read-only text field consumes clicks rather than exposing an onClick, so the tap is read
    // off its interaction source instead.
    LaunchedEffect(interaction) {
        interaction.interactions.collect { event ->
            if (event is PressInteraction.Release) picking = true
        }
    }

    OutlinedTextField(
        value = value,
        onValueChange = {},
        modifier = modifier,
        readOnly = true,
        label = { Text(stringResource(label)) },
        trailingIcon = {
            Icon(Icons.Outlined.Schedule, contentDescription = stringResource(R.string.select_time))
        },
        isError = minutes == null,
        interactionSource = interaction,
        singleLine = true,
    )

    if (picking) {
        val state = rememberTimePickerState(
            initialHour = (minutes ?: 0) / 60,
            initialMinute = (minutes ?: 0) % 60,
            is24Hour = DateFormat.is24HourFormat(LocalContext.current),
        )
        AlertDialog(
            onDismissRequest = { picking = false },
            title = { Text(stringResource(label)) },
            text = {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TimePicker(state = state)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onValueChange("%02d:%02d".format(state.hour, state.minute))
                    picking = false
                }) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { picking = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

private fun parseClock(value: String): Int? = runCatching {
    LocalTime.parse(value, DateTimeFormatter.ofPattern("HH:mm")).let { it.hour * 60 + it.minute }
}.getOrNull()

/**
 * Stat tiles laid out in rows of [columns] rather than one long row.
 *
 * Four tiles across a phone leaves each figure about eight characters of room, and the values here
 * are things like "3d 4h" and "1,240" — they wrapped mid-word or clipped. Two per row is wide
 * enough for every value the app can produce.
 */
@Composable
internal fun StatGrid(
    stats: List<Pair<String, String>>,
    modifier: Modifier = Modifier,
    columns: Int = 2,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        stats.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { (value, label) ->
                    StatTile(value = value, label = label, modifier = Modifier.weight(1f))
                }
                // Keeps a trailing odd tile the same width as the ones above it instead of letting
                // it stretch across the row.
                repeat(columns - row.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}
