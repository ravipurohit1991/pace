package com.pace.reduction.feature

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Handyman
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pace.reduction.R
import com.pace.reduction.domain.model.Achievement

/** Every badge Pace can award, in the order they usually arrive. */
internal val ALL_BADGE_IDS = listOf(
    "first_pause",
    "honest_week",
    "space_maker",
    "morning_reclaimed",
    "steady_three",
    "tool_builder",
    "ten_avoided",
    "reward_step",
)

private data class BadgeVisual(val icon: ImageVector, val tint: Color)

private fun badgeVisual(id: String): BadgeVisual = when (id) {
    "first_pause" -> BadgeVisual(Icons.Filled.SelfImprovement, Color(0xFF4E8A62))
    "honest_week" -> BadgeVisual(Icons.Filled.Verified, Color(0xFF3E7CA6))
    "space_maker" -> BadgeVisual(Icons.Filled.OpenInFull, Color(0xFF6E5AA6))
    "morning_reclaimed" -> BadgeVisual(Icons.Filled.WbSunny, Color(0xFFC98A2B))
    "steady_three" -> BadgeVisual(Icons.Filled.TrendingDown, Color(0xFF2F8C87))
    "tool_builder" -> BadgeVisual(Icons.Filled.Handyman, Color(0xFF8A6246))
    "ten_avoided" -> BadgeVisual(Icons.Filled.Savings, Color(0xFF4B7F3F))
    "reward_step" -> BadgeVisual(Icons.Filled.CardGiftcard, Color(0xFFB4566F))
    else -> BadgeVisual(Icons.Filled.EmojiEvents, Color(0xFF6E7B72))
}

/** Grid of every badge, earned ones in colour and the rest dimmed so there is something to aim at. */
@Composable
internal fun BadgeGrid(earned: List<Achievement>) {
    val earnedIds = earned.map { it.badgeId }.toSet()
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ALL_BADGE_IDS.forEach { id ->
            BadgeTile(id = id, unlocked = id in earnedIds)
        }
    }
}

@Composable
private fun BadgeTile(id: String, unlocked: Boolean) {
    val visual = badgeVisual(id)
    val container = if (unlocked) {
        visual.tint.copy(alpha = 0.16f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }
    val content = if (unlocked) visual.tint else MaterialTheme.colorScheme.outline

    Column(
        modifier = Modifier.width(76.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.size(56.dp).background(container, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = visual.icon,
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(28.dp),
            )
        }
        Text(
            text = badgeTitleFor(id),
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            fontWeight = if (unlocked) FontWeight.SemiBold else FontWeight.Normal,
            color = if (unlocked) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
internal fun badgeTitleFor(id: String): String = stringResource(
    when (id) {
        "first_pause" -> R.string.badge_first_pause
        "honest_week" -> R.string.badge_honest_week
        "space_maker" -> R.string.badge_space_maker
        "morning_reclaimed" -> R.string.badge_morning_reclaimed
        "steady_three" -> R.string.badge_steady_three
        "tool_builder" -> R.string.badge_tool_builder
        "ten_avoided" -> R.string.badge_ten_avoided
        "reward_step" -> R.string.badge_reward_step
        else -> R.string.badge_progress
    },
)
