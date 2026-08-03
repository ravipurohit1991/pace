package com.pace.reduction.domain

/**
 * The full badge catalogue, generated from tiered families rather than hand-written one by one.
 *
 * The point of the low tiers is that the first hour matters as much as the first year — someone
 * three hours in needs something to have already earned, not a distant milestone.
 */
enum class BadgeFamily(val id: String, val unit: String) {
    CLEAN_HOURS("clean_hours", "hours"),
    CLEAN_DAYS("clean_days", "days"),
    CLEAR_STREAK("clear_streak", "days"),
    RESISTED("resisted", "moments"),
    SAVED("saved", "money"),
    STEADY_DAYS("steady_days", "days"),
    TOOLS("tools", "sessions"),
    CONVERSATIONS("conversations", "chats"),
    LONGEST_WAIT("longest_wait", "minutes"),
    TIME_BACK("time_back", "minutes"),
}

data class BadgeDefinition(
    val id: String,
    val family: BadgeFamily,
    val threshold: Long,
    /** 0-based position within its family, used for tier colouring. */
    val tier: Int,
)

object BadgeCatalogue {
    private val CLEAN_HOURS = listOf(1L, 2, 3, 4, 6, 8, 10, 12, 18, 24, 36, 48, 72, 96, 120, 168, 240, 336, 504, 720, 1440, 2160, 4320, 8760)
    private val CLEAN_DAYS = listOf(1L, 2, 3, 4, 5, 6, 7, 10, 14, 21, 30, 45, 60, 90, 120, 150, 180, 240, 300, 365, 500, 730, 1095)
    private val CLEAR_STREAK = listOf(1L, 2, 3, 4, 5, 6, 7, 8, 9, 10, 12, 14, 16, 18, 21, 24, 28, 30, 40, 50, 60, 75, 90, 120, 180, 270, 365)
    private val RESISTED = listOf(1L, 2, 3, 5, 8, 10, 15, 20, 25, 30, 40, 50, 75, 100, 150, 200, 250, 300, 400, 500, 750, 1000, 1500, 2000, 3000, 5000)
    private val SAVED = listOf(1L, 2, 5, 10, 20, 30, 50, 75, 100, 150, 200, 300, 500, 750, 1000, 1500, 2000, 3000, 5000, 7500, 10000)
    private val STEADY_DAYS = listOf(1L, 2, 3, 5, 7, 10, 14, 20, 25, 30, 40, 50, 65, 80, 100, 125, 150, 200, 250, 300, 365)
    private val TOOLS = listOf(1L, 2, 3, 5, 8, 10, 15, 20, 30, 40, 50, 75, 100, 150, 200, 300, 500)
    private val CONVERSATIONS = listOf(1L, 2, 3, 5, 8, 10, 15, 20, 30, 50, 75, 100, 150, 200, 300, 500)
    private val LONGEST_WAIT = listOf(30L, 45, 60, 90, 120, 150, 180, 240, 300, 360, 480, 600, 720, 900, 1080, 1440)
    private val TIME_BACK = listOf(11L, 30, 60, 120, 240, 480, 720, 1440, 2880, 5760, 10080, 20160, 43200, 86400, 129600, 259200)

    private val thresholds: Map<BadgeFamily, List<Long>> = mapOf(
        BadgeFamily.CLEAN_HOURS to CLEAN_HOURS,
        BadgeFamily.CLEAN_DAYS to CLEAN_DAYS,
        BadgeFamily.CLEAR_STREAK to CLEAR_STREAK,
        BadgeFamily.RESISTED to RESISTED,
        BadgeFamily.SAVED to SAVED,
        BadgeFamily.STEADY_DAYS to STEADY_DAYS,
        BadgeFamily.TOOLS to TOOLS,
        BadgeFamily.CONVERSATIONS to CONVERSATIONS,
        BadgeFamily.LONGEST_WAIT to LONGEST_WAIT,
        BadgeFamily.TIME_BACK to TIME_BACK,
    )

    /** Every badge Pace can award, ordered by family then threshold. */
    val all: List<BadgeDefinition> = thresholds.flatMap { (family, values) ->
        values.sorted().mapIndexed { index, threshold ->
            BadgeDefinition(
                id = "${family.id}_$threshold",
                family = family,
                threshold = threshold,
                tier = index,
            )
        }
    }

    private val byId: Map<String, BadgeDefinition> = all.associateBy(BadgeDefinition::id)

    val size: Int get() = all.size

    fun definition(id: String): BadgeDefinition? = byId[id]

    fun forFamily(family: BadgeFamily): List<BadgeDefinition> =
        all.filter { it.family == family }

    /** Every badge whose threshold the supplied totals have reached. */
    fun earned(totals: Map<BadgeFamily, Long>): List<BadgeDefinition> =
        all.filter { definition ->
            (totals[definition.family] ?: 0L) >= definition.threshold
        }

    /** The next unearned badge in each family, for "what am I working toward". */
    fun nextUp(totals: Map<BadgeFamily, Long>): List<Pair<BadgeDefinition, Long>> =
        BadgeFamily.entries.mapNotNull { family ->
            val total = totals[family] ?: 0L
            forFamily(family).firstOrNull { it.threshold > total }?.let { it to total }
        }
}
