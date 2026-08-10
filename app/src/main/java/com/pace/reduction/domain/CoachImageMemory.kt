package com.pace.reduction.domain

/**
 * Keeps a compact, text-only memory of an attached image in conversation history. The image bytes
 * are sent once and discarded; later turns receive this model-authored visual description.
 */
object CoachImageMemory {
    private const val OPEN = "[[PACE_IMAGE_CONTEXT]]"
    private const val CLOSE = "[[/PACE_IMAGE_CONTEXT]]"
    private const val LEGACY_IMAGE_PREFIX = "📷 "
    private const val MAX_DESCRIPTION_CHARS = 900

    fun encode(visiblePrompt: String, description: String): String {
        val prompt = displayContent(visiblePrompt).trim()
        val memory = description
            .replace(OPEN, "")
            .replace(CLOSE, "")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(MAX_DESCRIPTION_CHARS)
        return if (memory.isBlank()) prompt else "$prompt\n\n$OPEN$memory$CLOSE"
    }

    fun displayContent(stored: String): String = stored
        .substringBefore(OPEN)
        .removePrefix(LEGACY_IMAGE_PREFIX)
        .trim()

    fun hasImage(stored: String): Boolean =
        stored.startsWith(LEGACY_IMAGE_PREFIX) || (stored.contains(OPEN) && stored.contains(CLOSE))

    fun modelContent(stored: String): String {
        val prompt = displayContent(stored)
        val memory = stored.substringAfter(OPEN, "").substringBefore(CLOSE, "").trim()
        return if (memory.isBlank()) {
            if (hasImage(stored)) {
                "$prompt\n\nA photo was attached to this earlier message, but no visual memory is available."
            } else {
                prompt
            }
        } else {
            "$prompt\n\nText-only memory of the photo attached with this message: $memory"
        }
    }
}
