package com.pace.reduction.domain

/**
 * How many stat pills a widget of a given width can show.
 *
 * Glance has no way to ask what a row of text measures, and a Row that overflows is silently
 * clipped — a half-cut pill at the edge reads as a bug. So the widths are estimated from the label
 * lengths against the one font size the pills use, and the row is filled in priority order until
 * the next pill would not fit.
 *
 * The estimate is deliberately generous: showing one pill fewer than would have fitted costs a
 * figure the user can still find in the app, while showing one too many damages the whole row.
 */
object WidgetStatFit {

    /** Padding inside a pill, both sides, from the widget's own pill style. */
    private const val PILL_PADDING_DP = 16.0

    /** Gap between pills. */
    private const val GAP_DP = 6.0

    /** Upper bound on the advance of a character at the pills' 11sp, rounded up for wide fonts. */
    private const val CHAR_WIDTH_DP = 6.6

    fun widthOf(label: String): Double = label.length * CHAR_WIDTH_DP + PILL_PADDING_DP

    /**
     * @param labels the pills that could be shown, most worth showing first.
     * @param availableDp the row's width, already less the widget's horizontal padding.
     */
    fun fit(labels: List<String>, availableDp: Double): List<String> {
        var used = 0.0
        return labels.takeWhile { label ->
            val next = used + widthOf(label) + if (used == 0.0) 0.0 else GAP_DP
            (next <= availableDp).also { if (it) used = next }
        }
    }
}
