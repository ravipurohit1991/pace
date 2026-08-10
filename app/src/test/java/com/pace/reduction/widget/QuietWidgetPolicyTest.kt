package com.pace.reduction.widget

import com.pace.reduction.proto.WidgetStateProto
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuietWidgetPolicyTest {
    @Test
    fun overnightRestNeverParticipatesInCountdownOrPulse() {
        assertTrue(WidgetStateProto.WIDGET_STATE_SPACING in COUNTDOWN_STATES)
        assertTrue(WidgetStateProto.WIDGET_STATE_MORNING_HOLD in COUNTDOWN_STATES)
        assertFalse(WidgetStateProto.WIDGET_STATE_REST in COUNTDOWN_STATES)
    }
}
