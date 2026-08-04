package com.pace.reduction.feature

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Handyman
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pace.reduction.R
import com.pace.reduction.core.designsystem.LocalMotion
import com.pace.reduction.core.designsystem.breathe
import com.pace.reduction.core.designsystem.entrance
import com.pace.reduction.domain.BadgeCatalogue
import com.pace.reduction.domain.BadgeFamily
import com.pace.reduction.domain.model.Achievement

private data class FamilyVisual(val icon: ImageVector, val tint: Color, val label: Int)

private fun visualFor(family: BadgeFamily): FamilyVisual = when (family) {
    BadgeFamily.CLEAN_HOURS -> FamilyVisual(Icons.Filled.Timer, Color(0xFF4E8A62), R.string.family_clean_hours)
    BadgeFamily.CLEAN_DAYS -> FamilyVisual(Icons.Filled.WbSunny, Color(0xFFC98A2B), R.string.family_clean_days)
    BadgeFamily.CLEAR_STREAK -> FamilyVisual(Icons.Filled.LocalFireDepartment, Color(0xFFC05B3C), R.string.family_clear_streak)
    BadgeFamily.RESISTED -> FamilyVisual(Icons.Filled.Shield, Color(0xFF3E7CA6), R.string.family_resisted)
    BadgeFamily.SAVED -> FamilyVisual(Icons.Filled.Savings, Color(0xFF4B7F3F), R.string.family_saved)
    BadgeFamily.STEADY_DAYS -> FamilyVisual(Icons.Filled.TrendingUp, Color(0xFF2F8C87), R.string.family_steady_days)
    BadgeFamily.TOOLS -> FamilyVisual(Icons.Filled.Handyman, Color(0xFF8A6246), R.string.family_tools)
    BadgeFamily.CONVERSATIONS -> FamilyVisual(Icons.Filled.Forum, Color(0xFF6E5AA6), R.string.family_conversations)
    BadgeFamily.LONGEST_WAIT -> FamilyVisual(Icons.Filled.HourglassBottom, Color(0xFF7A6C9B), R.string.family_longest_wait)
    BadgeFamily.TIME_BACK -> FamilyVisual(Icons.Filled.Bolt, Color(0xFFB4566F), R.string.family_time_back)
}

/**
 * Badges grouped by family rather than listed flat — with 200+ of them a flat grid is noise.
 * Each row shows how many tiers are earned and how close the next one is.
 */
@Composable
internal fun BadgeFamilyList(earned: List<Achievement>) {
    val earnedIds = earned.map { it.badgeId }.toSet()
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        BadgeFamily.entries.forEachIndexed { index, family ->
            val tiers = BadgeCatalogue.forFamily(family)
            val earnedTiers = tiers.filter { it.id in earnedIds }
            val next = tiers.firstOrNull { it.id !in earnedIds }
            FamilyRow(
                family = family,
                earnedCount = earnedTiers.size,
                totalCount = tiers.size,
                bestThreshold = earnedTiers.maxOfOrNull { it.threshold },
                nextThreshold = next?.threshold,
                index = index,
            )
        }
    }
}

@Composable
private fun FamilyRow(
    family: BadgeFamily,
    earnedCount: Int,
    totalCount: Int,
    bestThreshold: Long?,
    nextThreshold: Long?,
    index: Int,
) {
    val motion = LocalMotion.current
    val visual = visualFor(family)
    val unlocked = earnedCount > 0
    val complete = totalCount > 0 && earnedCount == totalCount
    val container = if (unlocked) visual.tint.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant
    val content = if (unlocked) visual.tint else MaterialTheme.colorScheme.outline
    // The bar fills rather than appears, so opening Progress plays back the earning of it.
    val progress by animateFloatAsState(
        targetValue = if (totalCount == 0) 0f else earnedCount.toFloat() / totalCount,
        animationSpec = motion.eased(700),
        label = "familyProgress",
    )

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.entrance(index)) {
        Box(
            modifier = Modifier
                .size(46.dp)
                // Only a finished family gets a heartbeat. If every row pulsed, none would read
                // as an achievement.
                .then(if (complete) Modifier.breathe(0.94f, 1.06f, 3_200) else Modifier)
                .background(container, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(visual.icon, contentDescription = null, tint = content, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.size(12.dp))
        Column(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(visual.label),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (unlocked) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "$earnedCount/$totalCount",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(5.dp),
                color = if (unlocked) visual.tint else MaterialTheme.colorScheme.outline,
                // Stated rather than defaulted: the default track picks up the accent, and a
                // family with nothing earned then reads as a full bar.
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = when {
                    nextThreshold == null -> stringResource(R.string.family_complete)
                    bestThreshold == null -> stringResource(R.string.family_first, nextThreshold.toString())
                    else -> stringResource(R.string.family_next, nextThreshold.toString())
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The most recent handful of badges, shown on Today as a small celebration strip. */
@Composable
internal fun RecentBadgeStrip(earned: List<Achievement>) {
    if (earned.isEmpty()) return
    val recent = earned.sortedByDescending { it.unlockedAt }.take(6)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        recent.forEach { achievement ->
            val family = BadgeCatalogue.definition(achievement.badgeId)?.family ?: return@forEach
            val visual = visualFor(family)
            Box(
                modifier = Modifier.size(38.dp).background(visual.tint.copy(alpha = 0.16f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(visual.icon, contentDescription = null, tint = visual.tint, modifier = Modifier.size(20.dp))
            }
        }
    }
}
