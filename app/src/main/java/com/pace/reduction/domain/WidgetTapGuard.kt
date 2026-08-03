package com.pace.reduction.domain

object WidgetTapGuard {
    const val DEBOUNCE_MILLIS = 1_500L

    fun shouldReuseLatest(source: String?, recordedAtEpochMs: Long?, nowEpochMs: Long): Boolean =
        source == "WIDGET" && recordedAtEpochMs != null &&
            nowEpochMs >= recordedAtEpochMs && nowEpochMs - recordedAtEpochMs < DEBOUNCE_MILLIS
}
