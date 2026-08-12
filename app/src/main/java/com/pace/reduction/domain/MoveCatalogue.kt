package com.pace.reduction.domain

enum class MoveKind { WALK, RUN, STRENGTH, MOBILITY }

/** One instruction in a session, held for [seconds]. Ids are resolved to copy by the UI. */
data class MoveStep(val id: String, val seconds: Int)

/**
 * A guided movement session.
 *
 * [stepTarget] is only set for the sessions the pedometer can actually verify. Everything else
 * finishes on the clock, because a phone on a mat cannot tell a push-up from a nap.
 */
data class MoveSession(
    val id: String,
    val kind: MoveKind,
    val steps: List<MoveStep>,
    val stepTarget: Int = 0,
) {
    val totalSeconds: Int get() = steps.sumOf { it.seconds }
    val minutes: Int get() = (totalSeconds + 30) / 60
    val verifiedByPedometer: Boolean get() = stepTarget > 0
}

/** Where a running session is at a given moment. */
data class MoveTick(
    val index: Int,
    val secondsLeftInStep: Int,
    val stepProgress: Float,
    val sessionProgress: Float,
    val finished: Boolean,
)

/**
 * The movement half of the toolkit.
 *
 * Everything else in the toolkit works on the head. This works on the body, which is the one
 * intervention with a dose-response behind it: a single bout cuts the wanting for the next half
 * hour or so, at intensities as low as a slow fifteen-minute walk. So the sessions are short,
 * specific, and doable in the clothes someone is already wearing — the point is to start one, not
 * to train.
 */
object MoveCatalogue {
    const val WALK_RESET = "walk_reset"
    const val WALK_LONG = "walk_long"
    const val RUN_INTERVALS = "run_intervals"
    const val PUSHUP_LADDER = "pushup_ladder"
    const val CIRCUIT = "circuit"
    const val YOGA_MORNING = "yoga_morning"
    const val YOGA_WIND_DOWN = "yoga_wind_down"
    const val DESK_RESET = "desk_reset"

    /** Steps per minute assumed when turning a walking session into a pedometer target. */
    private const val STEPS_PER_MINUTE = 105

    val all: List<MoveSession> = listOf(
        MoveSession(
            id = WALK_RESET,
            kind = MoveKind.WALK,
            steps = listOf(
                MoveStep("walk_out", 150),
                MoveStep("walk_back", 150),
            ),
            stepTarget = 5 * STEPS_PER_MINUTE,
        ),
        MoveSession(
            id = WALK_LONG,
            kind = MoveKind.WALK,
            steps = listOf(
                MoveStep("walk_easy", 180),
                MoveStep("walk_brisk", 480),
                MoveStep("walk_notice", 180),
                MoveStep("walk_easy", 60),
            ),
            stepTarget = 15 * STEPS_PER_MINUTE,
        ),
        MoveSession(
            id = RUN_INTERVALS,
            kind = MoveKind.RUN,
            steps = listOf(
                MoveStep("run_warm", 150),
                MoveStep("run_push", 60),
                MoveStep("run_float", 90),
                MoveStep("run_push", 60),
                MoveStep("run_float", 90),
                MoveStep("run_push", 60),
                MoveStep("run_cool", 150),
            ),
            stepTarget = 11 * STEPS_PER_MINUTE,
        ),
        MoveSession(
            id = PUSHUP_LADDER,
            kind = MoveKind.STRENGTH,
            steps = listOf(
                MoveStep("pushup_prep", 30),
                MoveStep("pushup_five", 40),
                MoveStep("rest_shake", 30),
                MoveStep("pushup_eight", 50),
                MoveStep("rest_shake", 40),
                MoveStep("pushup_ten", 60),
                MoveStep("rest_shake", 40),
                MoveStep("pushup_max", 60),
                MoveStep("pushup_finish", 30),
            ),
        ),
        MoveSession(
            id = CIRCUIT,
            kind = MoveKind.STRENGTH,
            steps = listOf(
                MoveStep("circuit_march", 45),
                MoveStep("circuit_squat", 45),
                MoveStep("circuit_pushup", 40),
                MoveStep("circuit_plank", 30),
                MoveStep("rest_shake", 40),
                MoveStep("circuit_squat", 45),
                MoveStep("circuit_pushup", 40),
                MoveStep("circuit_plank", 30),
                MoveStep("circuit_breathe", 45),
            ),
        ),
        MoveSession(
            id = YOGA_MORNING,
            kind = MoveKind.MOBILITY,
            steps = listOf(
                MoveStep("yoga_stand_tall", 40),
                MoveStep("yoga_cat_cow", 60),
                MoveStep("yoga_down_dog", 50),
                MoveStep("yoga_lunge_left", 45),
                MoveStep("yoga_lunge_right", 45),
                MoveStep("yoga_forward_fold", 45),
                MoveStep("yoga_chest_open", 45),
                MoveStep("yoga_reach_up", 40),
            ),
        ),
        MoveSession(
            id = YOGA_WIND_DOWN,
            kind = MoveKind.MOBILITY,
            steps = listOf(
                MoveStep("yoga_child", 60),
                MoveStep("yoga_twist_left", 50),
                MoveStep("yoga_twist_right", 50),
                MoveStep("yoga_figure_four_left", 50),
                MoveStep("yoga_figure_four_right", 50),
                MoveStep("yoga_legs_up", 70),
                MoveStep("yoga_settle", 70),
            ),
        ),
        MoveSession(
            id = DESK_RESET,
            kind = MoveKind.MOBILITY,
            steps = listOf(
                MoveStep("desk_stand", 25),
                MoveStep("desk_shoulder_rolls", 30),
                MoveStep("desk_neck_left", 25),
                MoveStep("desk_neck_right", 25),
                MoveStep("desk_chest_open", 35),
                MoveStep("desk_twist", 35),
            ),
        ),
    )

    fun byId(id: String): MoveSession = all.firstOrNull { it.id == id } ?: all.first()

    /**
     * The one session to put in front of someone right now.
     *
     * Reads the hour rather than asking: a wind-down flow at nine in the morning and a run at
     * bedtime are both technically valid answers and neither is the one a person would pick.
     * Walking beats everything in the middle of the day because it needs no floor space, no
     * changing, and no decision beyond the front door.
     */
    fun suggestFor(
        minuteOfDay: Int,
        wakeMinutes: Int,
        sleepMinutes: Int,
        stepsToday: Long = 0,
        averageSteps: Double = 0.0,
    ): MoveSession {
        val sinceWake = ((minuteOfDay - wakeMinutes) + 24 * 60) % (24 * 60)
        val untilSleep = ((sleepMinutes - minuteOfDay) + 24 * 60) % (24 * 60)
        return when {
            untilSleep in 1..120 -> byId(YOGA_WIND_DOWN)
            sinceWake <= 120 -> byId(YOGA_MORNING)
            // Behind on the usual day's walking, and enough daylight left to fix it.
            averageSteps > 0 && stepsToday < averageSteps * 0.6 && untilSleep > 180 -> byId(WALK_LONG)
            minuteOfDay in (13 * 60)..(16 * 60) -> byId(WALK_RESET)
            else -> byId(DESK_RESET)
        }
    }

    /** Sessions offered on the toolkit shelf, shortest first so the cheap yes is at the top. */
    val shelf: List<MoveSession> = listOf(
        byId(DESK_RESET),
        byId(WALK_RESET),
        byId(YOGA_MORNING),
        byId(PUSHUP_LADDER),
        byId(CIRCUIT),
        byId(YOGA_WIND_DOWN),
        byId(RUN_INTERVALS),
        byId(WALK_LONG),
    )
}

/**
 * What should be on screen [elapsedMs] into a session.
 *
 * Pure, like every other tool's schedule: the step showing is a function of elapsed time, never of
 * how many frames have been drawn, so a dropped frame cannot desynchronise the instruction from the
 * countdown beside it.
 */
fun MoveSession.tickAt(elapsedMs: Long): MoveTick {
    val elapsedSeconds = (elapsedMs / 1_000L).coerceAtLeast(0L)
    val total = totalSeconds.toLong()
    if (elapsedSeconds >= total) {
        return MoveTick(
            index = steps.lastIndex,
            secondsLeftInStep = 0,
            stepProgress = 1f,
            sessionProgress = 1f,
            finished = true,
        )
    }
    var consumed = 0L
    steps.forEachIndexed { index, step ->
        val end = consumed + step.seconds
        if (elapsedSeconds < end) {
            val into = elapsedSeconds - consumed
            return MoveTick(
                index = index,
                secondsLeftInStep = (step.seconds - into).toInt(),
                stepProgress = into.toFloat() / step.seconds,
                sessionProgress = elapsedSeconds.toFloat() / total,
                finished = false,
            )
        }
        consumed = end
    }
    return MoveTick(steps.lastIndex, 0, 1f, 1f, true)
}
