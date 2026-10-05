package com.pace.reduction.domain

import com.pace.reduction.domain.model.UrgeSession
import java.time.Duration
import java.time.Instant

/** How one tool has gone for this person, from their own finished sessions. */
data class ToolStat(
    val toolId: String,
    val uses: Int,
    /** Sessions with a before *and* after rating — the only ones that can show a change. */
    val rated: Int,
    /** Mean fall on the 1–5 scale across rated sessions, or null with none rated. */
    val averageDrop: Double?,
    val lastUsed: Instant,
) {
    /** Enough evidence to say "this works for you" rather than "you have used this". */
    val proven: Boolean get() = rated >= MIN_RATED && (averageDrop ?: 0.0) >= 1.0

    companion object {
        const val MIN_RATED = 2
    }
}

/**
 * Which tools someone actually reaches for, and which ones actually move the number.
 *
 * Everything here is computed on the device from sessions already stored, so it works with the
 * coach switched off and gives the "pick for me" agent something real to reason from instead of a
 * guess.
 */
object ToolInsights {
    /** Sessions older than this say more about who someone was than who they are. */
    private val LOOKBACK: Duration = Duration.ofDays(60)

    fun calculate(sessions: List<UrgeSession>, now: Instant): List<ToolStat> {
        val since = now.minus(LOOKBACK)
        return sessions
            .asSequence()
            .filter { it.completed && !it.startedAt.isBefore(since) }
            .filter { ToolDirectory.byId(it.tool) != null }
            .groupBy { ToolDirectory.byId(it.tool)!!.id }
            .map { (id, uses) ->
                val drops = uses.mapNotNull { session ->
                    val before = session.urgeBefore ?: return@mapNotNull null
                    val after = session.urgeAfter ?: return@mapNotNull null
                    (before - after).toDouble()
                }
                ToolStat(
                    toolId = id,
                    uses = uses.size,
                    rated = drops.size,
                    averageDrop = drops.takeIf { it.isNotEmpty() }?.average(),
                    lastUsed = uses.maxOf { it.startedAt },
                )
            }
            .sortedWith(
                compareByDescending<ToolStat> { it.proven }
                    .thenByDescending { it.averageDrop ?: Double.NEGATIVE_INFINITY }
                    .thenByDescending { it.uses }
                    .thenByDescending { it.lastUsed },
            )
    }

    /** The last few distinct tools used, newest first — a "pick up where you left off" row. */
    fun recent(sessions: List<UrgeSession>, limit: Int = 3): List<String> = sessions
        .asSequence()
        .filter { it.completed }
        .sortedByDescending { it.startedAt }
        .mapNotNull { ToolDirectory.byId(it.tool)?.id }
        .distinct()
        .take(limit)
        .toList()
}

/** Why the on-device picker chose what it chose, so the UI can say it in its own words. */
enum class AutopilotReason { STRONG_MOMENT, PROVEN_FOR_YOU, LATE_HOUR, GET_MOVING, FRESH_DISTRACTION, MODEL }

data class AutopilotPick(
    val toolId: String,
    /** The model's own line, or null when the pick was made on the device. */
    val message: String?,
    val reason: AutopilotReason,
)

/**
 * "Pick for me": the one decision a bad minute is worst at.
 *
 * The model is the first choice, because it can weigh the hour, the history and what was just said
 * in the chat all at once. This is the fallback that runs with no key and no signal, and it is not
 * random either — it leans on what has measurably worked for this person before.
 */
object Autopilot {
    fun fallback(
        strength: Int?,
        minuteOfDay: Int,
        stats: List<ToolStat>,
        recent: List<String>,
        coachReady: Boolean,
        seed: Long,
    ): AutopilotPick {
        val usable = ToolDirectory.available(coachReady).map { it.id }.toSet()
        val proven = stats.firstOrNull { it.proven && it.toolId in usable }
        val last = recent.firstOrNull()
        val hour = minuteOfDay / 60

        if ((strength ?: 0) >= 4) {
            val calm = listOf(ToolDirectory.BREATHING, ToolDirectory.URGE_SURF)
            val pick = proven?.toolId?.takeIf { it in calm }
                ?: calm.firstOrNull { it != last }
                ?: ToolDirectory.BREATHING
            return AutopilotPick(pick, null, AutopilotReason.STRONG_MOMENT)
        }
        if (proven != null && proven.toolId != last) {
            return AutopilotPick(proven.toolId, null, AutopilotReason.PROVEN_FOR_YOU)
        }
        if (hour >= 22 || hour < 6) {
            val pick = if (last == ToolDirectory.GROUNDING) ToolDirectory.BREATHING else ToolDirectory.GROUNDING
            return AutopilotPick(pick, null, AutopilotReason.LATE_HOUR)
        }
        if (hour in 10..18 && recent.none { it.startsWith(ToolDirectory.MOVE_PREFIX) }) {
            return AutopilotPick(ToolDirectory.MOVE_PREFIX + MoveCatalogue.DESK_RESET, null, AutopilotReason.GET_MOVING)
        }
        val distractions = listOf(
            ToolDirectory.TRIVIA,
            ToolDirectory.EMOJI,
            ToolDirectory.BLOCKS,
            ToolDirectory.MEMORY,
            ToolDirectory.CHANGE_PLACE,
        ).filter { it in usable && it !in recent }
        val pick = distractions.shuffled(kotlin.random.Random(seed)).firstOrNull() ?: ToolDirectory.TRIVIA
        return AutopilotPick(pick, null, AutopilotReason.FRESH_DISTRACTION)
    }

    /** Reads the model's `{tool, message}`; null when it chose something the app cannot open. */
    fun parse(raw: String, coachReady: Boolean = true): AutopilotPick? {
        val cleaned = ArcadeParser.stripFences(raw)
        val tool = Regex(""""tool"\s*:\s*"([^"]{1,40})"""").find(cleaned)?.groupValues?.get(1)
        val entry = ToolDirectory.byId(tool)?.takeIf { coachReady || !it.needsCoach } ?: return null
        val message = Regex(""""message"\s*:\s*"((?:[^"\\]|\\.){1,400})"""").find(cleaned)
            ?.groupValues?.get(1)
            ?.replace("\\n", " ")
            ?.replace("\\\"", "\"")
            ?.trim()
            ?.takeIf { it.isNotEmpty() && !TriggerWords.contains(it) }
        return AutopilotPick(entry.id, message, AutopilotReason.MODEL)
    }

    val schema = ArcadeParser.Schemas.objectSchema(
        "tool" to ArcadeParser.Schemas.stringSchema(),
        "message" to ArcadeParser.Schemas.stringSchema(),
    )
}
