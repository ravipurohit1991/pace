package com.pace.reduction.feature

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pace.reduction.R
import com.pace.reduction.core.designsystem.LocalMotion
import com.pace.reduction.core.designsystem.widgetBackdrop
import com.pace.reduction.domain.WidgetStatFit
import com.pace.reduction.domain.model.AccentPalette
import com.pace.reduction.domain.model.MotionLevel
import com.pace.reduction.domain.model.PlanSettings
import com.pace.reduction.domain.model.ThemeMode
import com.pace.reduction.domain.model.WidgetBackground
import com.pace.reduction.domain.model.WidgetSettings
import com.pace.reduction.domain.model.WidgetTick

/**
 * Theme, accent, and how much the interface is allowed to move.
 *
 * Nothing here has a Save button. Appearance is the one class of setting whose result the user is
 * already looking at, so applying on tap turns the section into the preview.
 */
@Composable
internal fun AppearanceSection(settings: PlanSettings, onChange: (PlanSettings) -> Unit) {
    val dynamicAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    SectionCard {
        Text(stringResource(R.string.appearance_title), style = MaterialTheme.typography.titleLarge)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemeMode.entries.forEach { option ->
                FilterChip(
                    selected = settings.themeMode == option,
                    onClick = { onChange(settings.copy(themeMode = option)) },
                    label = {
                        Text(
                            stringResource(
                                when (option) {
                                    ThemeMode.SYSTEM -> R.string.theme_system
                                    ThemeMode.LIGHT -> R.string.theme_light
                                    ThemeMode.DARK -> R.string.theme_dark
                                },
                            ),
                        )
                    },
                )
            }
        }

        Text(stringResource(R.string.accent_title), style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AccentPalette.entries.forEach { option ->
                AccentSwatch(
                    palette = option,
                    selected = !settings.dynamicColor && settings.accentPalette == option,
                    // Picking a colour by hand is a clear statement that the wallpaper should
                    // stop deciding, so it turns Material You off rather than doing nothing.
                    onClick = { onChange(settings.copy(accentPalette = option, dynamicColor = false)) },
                )
            }
        }

        SettingSwitch(
            title = stringResource(R.string.dynamic_color_title),
            body = stringResource(
                if (dynamicAvailable) R.string.dynamic_color_body else R.string.dynamic_color_unavailable,
            ),
            checked = settings.dynamicColor && dynamicAvailable,
            enabled = dynamicAvailable,
            onCheckedChange = { onChange(settings.copy(dynamicColor = it)) },
        )

        SettingSwitch(
            title = stringResource(R.string.amoled_title),
            body = stringResource(R.string.amoled_body),
            checked = settings.amoledDark,
            onCheckedChange = { onChange(settings.copy(amoledDark = it)) },
        )

        Text(stringResource(R.string.motion_title), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.motion_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MotionLevel.entries.forEach { option ->
                FilterChip(
                    selected = settings.motionLevel == option,
                    onClick = { onChange(settings.copy(motionLevel = option)) },
                    label = {
                        Text(
                            stringResource(
                                when (option) {
                                    MotionLevel.FULL -> R.string.motion_full
                                    MotionLevel.SUBTLE -> R.string.motion_subtle
                                    MotionLevel.NONE -> R.string.motion_none
                                },
                            ),
                        )
                    },
                )
            }
        }
    }
}

/** A colour disc that grows a ring and a tick when chosen, so the choice is visible without a label. */
@Composable
private fun AccentSwatch(palette: AccentPalette, selected: Boolean, onClick: () -> Unit) {
    val motion = LocalMotion.current
    val dark = isSystemInDarkTheme()
    val backdrop = palette.widgetBackdrop(opacityPercent = 100, glass = false)
    val fill = if (dark) backdrop.night else backdrop.day
    val ring by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
        animationSpec = motion.eased(220),
        label = "swatchRing",
    )
    val discScale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.86f,
        animationSpec = motion.springy(),
        label = "swatchScale",
    )
    val name = stringResource(
        when (palette) {
            AccentPalette.SAGE -> R.string.accent_sage
            AccentPalette.OCEAN -> R.string.accent_ocean
            AccentPalette.EMBER -> R.string.accent_ember
            AccentPalette.VIOLET -> R.string.accent_violet
            AccentPalette.SLATE -> R.string.accent_slate
        },
    )
    val label = stringResource(
        if (selected) R.string.accent_selected else R.string.accent_unselected,
        name,
    )

    Box(
        modifier = Modifier
            .size(46.dp)
            .border(2.dp, ring, CircleShape)
            .padding(4.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .scale(discScale)
                .background(fill, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(
                    Icons.Outlined.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/**
 * Everything about the widget, with the widget itself on screen above the controls.
 *
 * A home-screen widget is the one surface a user cannot see while they configure it — they would
 * otherwise be changing a corner radius, leaving Settings, and coming back. The preview is not
 * decoration; it is what makes these controls usable at all.
 */
@Composable
internal fun WidgetSection(
    widget: WidgetSettings,
    accent: AccentPalette,
    dynamicColor: Boolean,
    installed: Boolean?,
    onRequestPin: () -> Unit,
    onChange: (WidgetSettings) -> Unit,
) {
    SectionCard {
        Text(stringResource(R.string.widget_settings_title), style = MaterialTheme.typography.titleLarge)
        Text(
            stringResource(R.string.widget_settings_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        WidgetPreview(widget = widget, accent = accent)
        Text(
            stringResource(R.string.widget_preview_hint),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Someone tuning the widget's appearance is exactly the person who has not pinned it yet,
        // so the offer belongs here rather than only on Today.
        if (installed == false) {
            Text(
                stringResource(R.string.widget_not_installed),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = onRequestPin, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.add_widget_action))
            }
        }

        Text(stringResource(R.string.widget_background_title), style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WidgetBackground.entries.forEach { option ->
                FilterChip(
                    selected = widget.background == option,
                    onClick = { onChange(widget.copy(background = option)) },
                    label = {
                        Text(
                            stringResource(
                                when (option) {
                                    WidgetBackground.GRADIENT -> R.string.widget_bg_gradient
                                    WidgetBackground.SOLID -> R.string.widget_bg_solid
                                    WidgetBackground.GLASS -> R.string.widget_bg_glass
                                },
                            ),
                        )
                    },
                )
            }
        }

        Text(
            stringResource(R.string.widget_corner_label, widget.cornerRadiusDp),
            style = MaterialTheme.typography.labelLarge,
        )
        Slider(
            value = widget.cornerRadiusDp.toFloat(),
            onValueChange = { onChange(widget.copy(cornerRadiusDp = it.toInt())) },
            valueRange = 0f..40f,
            steps = 7,
        )

        Text(
            stringResource(R.string.widget_opacity_label, widget.opacityPercent),
            style = MaterialTheme.typography.labelLarge,
        )
        Slider(
            value = widget.opacityPercent.toFloat(),
            onValueChange = { onChange(widget.copy(opacityPercent = it.toInt())) },
            // Below about a third the text stops being readable over a busy wallpaper, so the
            // slider simply does not go there.
            valueRange = 35f..100f,
            steps = 12,
        )

        Text(stringResource(R.string.widget_shows_title), style = MaterialTheme.typography.titleMedium)
        SettingSwitch(
            title = stringResource(R.string.widget_show_countdown),
            checked = widget.showCountdown,
            onCheckedChange = { onChange(widget.copy(showCountdown = it)) },
        )
        SettingSwitch(
            title = stringResource(R.string.widget_show_stats),
            checked = widget.showStats,
            onCheckedChange = { onChange(widget.copy(showStats = it)) },
        )
        SettingSwitch(
            title = stringResource(R.string.widget_show_streak),
            checked = widget.showStreak,
            onCheckedChange = { onChange(widget.copy(showStreak = it)) },
        )
        SettingSwitch(
            title = stringResource(R.string.widget_show_steps),
            body = stringResource(R.string.widget_show_steps_body),
            checked = widget.showSteps,
            onCheckedChange = { onChange(widget.copy(showSteps = it)) },
        )
        SettingSwitch(
            title = stringResource(R.string.widget_show_quote),
            checked = widget.showQuote,
            onCheckedChange = { onChange(widget.copy(showQuote = it)) },
        )
        SettingSwitch(
            title = stringResource(R.string.widget_show_actions),
            checked = widget.showActions,
            onCheckedChange = { onChange(widget.copy(showActions = it)) },
        )

        SettingSwitch(
            title = stringResource(R.string.widget_pulse_title),
            body = stringResource(R.string.widget_pulse_body),
            checked = widget.livePulse,
            onCheckedChange = { onChange(widget.copy(livePulse = it)) },
        )
        SettingSwitch(
            title = stringResource(R.string.widget_confirm_title),
            body = stringResource(R.string.widget_confirm_body),
            checked = widget.confirmLog,
            onCheckedChange = { onChange(widget.copy(confirmLog = it)) },
        )

        Text(stringResource(R.string.widget_tick_title), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.widget_tick_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WidgetTick.entries.forEach { option ->
                FilterChip(
                    selected = widget.tick == option,
                    onClick = { onChange(widget.copy(tick = option)) },
                    label = {
                        Text(
                            stringResource(
                                when (option) {
                                    WidgetTick.SAVER -> R.string.widget_tick_saver
                                    WidgetTick.LIVE -> R.string.widget_tick_live
                                    WidgetTick.OFF -> R.string.widget_tick_off
                                },
                            ),
                        )
                    },
                )
            }
        }

        AnimatedVisibility(
            visible = widget.tick == WidgetTick.OFF,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Text(
                stringResource(R.string.widget_tick_off_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        OutlinedButton(
            onClick = { onChange(WidgetSettings()) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.widget_reset_defaults)) }

        if (dynamicColor) {
            Text(
                stringResource(R.string.widget_accent_note),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * A faithful-enough miniature of the home-screen widget.
 *
 * Deliberately a re-implementation rather than a rendering of the real Glance tree: Glance can only
 * draw into a widget host, and a screenshot of one would not respond to a slider. Everything that
 * changes here — fill, rounding, translucency, which rows exist — is read from the same settings
 * the widget reads, so the two cannot drift on the things the user is actually adjusting.
 */
@Composable
private fun WidgetPreview(widget: WidgetSettings, accent: AccentPalette) {
    val motion = LocalMotion.current
    val dark = isSystemInDarkTheme()
    val backdrop = accent.widgetBackdrop(
        opacityPercent = widget.opacityPercent,
        glass = widget.background == WidgetBackground.GLASS,
    )
    val fill = if (dark) backdrop.night else backdrop.day
    val shape = RoundedCornerShape(widget.cornerRadiusDp.dp)
    val onFill = Color.White
    val muted = Color.White.copy(alpha = 0.72f)

    // A checkerboard stands in for wallpaper, which is the only way translucency reads as
    // translucency rather than as a slightly different colour.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.surfaceVariant,
                        MaterialTheme.colorScheme.surfaceContainerHighest,
                        MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ),
                RoundedCornerShape(20.dp),
            )
            .padding(10.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(fill)
                .then(
                    if (widget.background == WidgetBackground.SOLID) {
                        Modifier
                    } else {
                        Modifier.background(
                            Brush.verticalGradient(
                                listOf(Color.White.copy(alpha = 0.20f), Color.Black.copy(alpha = 0.10f)),
                            ),
                        )
                    },
                )
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("4", color = onFill, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(4.dp))
                        Text("/ 12", color = muted, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                    Text(
                        stringResource(R.string.widget_spacing_until, "14:20"),
                        color = onFill,
                        fontSize = 12.sp,
                    )
                }
                if (widget.showCountdown && widget.tick != WidgetTick.OFF) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            stringResource(R.string.widget_remaining_hours, 1, 42),
                            color = onFill,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            stringResource(R.string.widget_until_next_label),
                            color = muted,
                            fontSize = 9.sp,
                        )
                    }
                }
            }

            // Ceiling meter.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.22f)),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(0.33f)
                        .height(6.dp)
                        .clip(CircleShape)
                        .background(Color.White),
                )
            }

            AnimatedVisibility(
                visible = widget.livePulse,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                SweepingBar(enabled = motion.enabled)
            }

            if (widget.showStats) {
                // Which pills survive is decided by the same rule the widget uses, so the preview
                // drops what the home screen would drop at the width it has here.
                BoxWithConstraints {
                    val availableDp = maxWidth.value.toDouble()
                    val labels = buildList {
                        add(stringResource(R.string.widget_free_for, "4h"))
                        if (widget.showSteps) add(stringResource(R.string.widget_steps, "3.4k"))
                        add(stringResource(R.string.widget_avoided, 8))
                        if (widget.showStreak) add(stringResource(R.string.widget_streak, 3))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        WidgetStatFit.fit(labels, availableDp).forEach { PreviewPill(it) }
                    }
                }
            }

            if (widget.showQuote) {
                Text(
                    stringResource(R.string.widget_message_default),
                    color = muted,
                    fontSize = 11.sp,
                )
            }

            if (widget.showActions) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PreviewAction(
                        label = stringResource(R.string.widget_talk),
                        icon = Icons.Outlined.Forum,
                        filled = true,
                        modifier = Modifier.weight(1f),
                    )
                    PreviewAction(
                        label = stringResource(R.string.widget_log_short),
                        icon = Icons.Outlined.Add,
                        filled = false,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/**
 * Stands in for the widget's indeterminate ProgressBar.
 *
 * Both are indeterminate bars sweeping the same way, so the switch above shows what turning it on
 * actually buys. With motion off it holds still, matching what an animation-free device does to the
 * real one.
 */
@Composable
private fun SweepingBar(enabled: Boolean) {
    val track = Color.White.copy(alpha = 0.18f)
    val lit = Color.White.copy(alpha = 0.7f)
    if (!enabled) {
        Box(Modifier.fillMaxWidth().height(3.dp).clip(CircleShape).background(track))
        return
    }
    LinearProgressIndicator(
        modifier = Modifier.fillMaxWidth().height(3.dp).clip(CircleShape),
        color = lit,
        trackColor = track,
        strokeCap = StrokeCap.Round,
        gapSize = 0.dp,
    )
}

@Composable
private fun PreviewPill(text: String) {
    Text(
        text,
        color = Color.White,
        fontSize = 10.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.18f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
private fun PreviewAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    filled: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (filled) Color.White else Color.White.copy(alpha = 0.18f))
            .padding(vertical = 7.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (filled) Color(0xFF1B2E22) else Color.White,
            modifier = Modifier.size(13.dp),
        )
        Spacer(Modifier.width(5.dp))
        Text(
            label,
            color = if (filled) Color(0xFF1B2E22) else Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** A titled switch with optional supporting text — the shape every toggle in Settings uses. */
@Composable
internal fun SettingSwitch(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    body: String? = null,
    enabled: Boolean = true,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (body != null) {
                Text(
                    body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}
