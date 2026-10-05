package com.pace.reduction.domain

import java.text.Normalizer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Games the model writes on the spot.
 *
 * A puzzle that is new every time is the one kind of distraction an app cannot ship in advance,
 * and novelty is most of what holds attention for the three or four minutes that matter. Each game
 * asks for one structured JSON payload, validates it hard, and then plays entirely on the device —
 * the network is touched once per round, not once per tap.
 */
@Serializable
data class TriviaQuestion(
    val question: String,
    val options: List<String>,
    @SerialName("answer_index") val answerIndex: Int,
    /** One line shown after answering, so a wrong answer still pays out something interesting. */
    val fact: String = "",
)

@Serializable
data class EmojiPuzzle(
    val emojis: String,
    val answer: String,
    val hint: String = "",
    val category: String = "",
)

@Serializable
data class StoryBeat(
    val scene: String,
    val choices: List<String> = emptyList(),
    val ending: Boolean = false,
)

@Serializable
private data class TriviaWire(val questions: List<TriviaQuestion> = emptyList())

@Serializable
private data class EmojiWire(val puzzles: List<EmojiPuzzle> = emptyList())

object TriggerWords {
    /**
     * The same vocabulary [CoachPrompt] forbids. Generated game content is filtered against it as
     * a backstop, because a trivia question is the one place a model might name it innocently.
     */
    private val WORDS = listOf(
        "cigar", "smok", "nicotin", "tobacco", "vape", "vaping", "craving", "puff", "lighter",
        "ashtray", "quitting", "marlboro", "camel cig",
    )

    fun contains(text: String): Boolean {
        val lower = text.lowercase()
        return WORDS.any { lower.contains(it) }
    }
}

object ArcadeParser {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    const val ROUND_SIZE = 5
    private const val MAX_TEXT = 220

    /** Models wrap JSON in fences even when given a schema; the content inside is still good. */
    fun stripFences(raw: String): String {
        val trimmed = raw.trim()
            .removePrefix("```json")
            .removePrefix("```JSON")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        val start = trimmed.indexOf('{')
        val end = trimmed.lastIndexOf('}')
        return if (start >= 0 && end > start) trimmed.substring(start, end + 1) else trimmed
    }

    fun trivia(raw: String): List<TriviaQuestion> {
        val parsed = runCatching { json.decodeFromString(TriviaWire.serializer(), stripFences(raw)) }
            .getOrNull() ?: return emptyList()
        return parsed.questions
            .mapNotNull(::cleanTrivia)
            .distinctBy { it.question.lowercase() }
            .take(ROUND_SIZE)
    }

    fun emoji(raw: String): List<EmojiPuzzle> {
        val parsed = runCatching { json.decodeFromString(EmojiWire.serializer(), stripFences(raw)) }
            .getOrNull() ?: return emptyList()
        return parsed.puzzles
            .mapNotNull(::cleanEmoji)
            .distinctBy { AnswerMatcher.normalise(it.answer) }
            .take(ROUND_SIZE)
    }

    fun story(raw: String): StoryBeat? {
        val parsed = runCatching { json.decodeFromString(StoryBeat.serializer(), stripFences(raw)) }
            .getOrNull() ?: return null
        val scene = parsed.scene.trim().take(900)
        if (scene.isBlank()) return null
        val choices = parsed.choices
            .map { it.trim().take(90) }
            .filter { it.isNotEmpty() && !TriggerWords.contains(it) }
            .distinct()
            .take(3)
        // A story that forgot to offer choices has ended, whatever the flag says.
        return StoryBeat(scene = scene, choices = if (parsed.ending) emptyList() else choices, ending = parsed.ending || choices.isEmpty())
    }

    private fun cleanTrivia(question: TriviaQuestion): TriviaQuestion? {
        val text = question.question.trim().take(MAX_TEXT)
        val options = question.options.map { it.trim().take(80) }
        if (text.isEmpty() || options.size !in 2..4 || options.any(String::isEmpty)) return null
        if (options.map(String::lowercase).distinct().size != options.size) return null
        if (question.answerIndex !in options.indices) return null
        if (TriggerWords.contains(text) || options.any(TriggerWords::contains)) return null
        val fact = question.fact.trim().take(MAX_TEXT).takeUnless(TriggerWords::contains).orEmpty()
        return TriviaQuestion(text, options, question.answerIndex, fact)
    }

    private fun cleanEmoji(puzzle: EmojiPuzzle): EmojiPuzzle? {
        val emojis = puzzle.emojis.trim().take(40)
        val answer = puzzle.answer.trim().take(60)
        if (emojis.isEmpty() || answer.isEmpty() || AnswerMatcher.normalise(answer).isEmpty()) return null
        // An emoji puzzle whose clue is mostly letters is just the answer written out.
        if (emojis.count(Char::isLetterOrDigit) > 2) return null
        if (TriggerWords.contains(answer) || TriggerWords.contains(puzzle.hint)) return null
        return EmojiPuzzle(emojis, answer, puzzle.hint.trim().take(120), puzzle.category.trim().take(30))
    }

    /** JSON schemas handed to Ollama's structured-output `format` field. */
    object Schemas {
        val trivia: JsonObject = objectSchema(
            "questions" to arraySchema(
                objectSchema(
                    "question" to stringSchema(),
                    "options" to arraySchema(stringSchema()),
                    "answer_index" to buildJsonObject { put("type", "integer") },
                    "fact" to stringSchema(),
                ),
            ),
        )

        val emoji: JsonObject = objectSchema(
            "puzzles" to arraySchema(
                objectSchema(
                    "emojis" to stringSchema(),
                    "answer" to stringSchema(),
                    "hint" to stringSchema(),
                    "category" to stringSchema(),
                ),
            ),
        )

        val story: JsonObject = objectSchema(
            "scene" to stringSchema(),
            "choices" to arraySchema(stringSchema()),
            "ending" to buildJsonObject { put("type", "boolean") },
        )

        internal fun stringSchema(): JsonObject = buildJsonObject { put("type", "string") }

        internal fun arraySchema(items: JsonObject): JsonObject = buildJsonObject {
            put("type", "array")
            put("items", items)
        }

        internal fun objectSchema(vararg properties: Pair<String, JsonObject>): JsonObject = buildJsonObject {
            put("type", "object")
            put("properties", JsonObject(properties.toMap()))
            put("required", JsonArray(properties.map { JsonPrimitive(it.first) }))
        }
    }
}

/**
 * Forgiving answer checking for typed guesses.
 *
 * Someone fighting the clock is not going to type "The Lion King" with the article and the right
 * capitals. Accents, punctuation, a leading article and a typo or two are all let through; a
 * different answer is not.
 */
object AnswerMatcher {
    private val ARTICLES = setOf("the", "a", "an")

    fun normalise(text: String): String {
        val stripped = Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace("&", " and ")
            .replace(Regex("[^a-z0-9 ]"), " ")
            .trim()
        return stripped.split(Regex("\\s+"))
            .filter { it.isNotEmpty() }
            .dropWhile { it in ARTICLES }
            .joinToString(" ")
    }

    fun matches(guess: String, answer: String): Boolean {
        val g = normalise(guess)
        val a = normalise(answer)
        if (g.isEmpty() || a.isEmpty()) return false
        if (g == a) return true
        if (g.replace(" ", "") == a.replace(" ", "")) return true
        val allowed = when {
            a.length >= 12 -> 2
            a.length >= 5 -> 1
            else -> 0
        }
        return allowed > 0 && distance(g, a) <= allowed
    }

    /** Plain Levenshtein; answers are short enough that the quadratic table is nothing. */
    internal fun distance(left: String, right: String): Int {
        if (left == right) return 0
        if (left.isEmpty()) return right.length
        if (right.isEmpty()) return left.length
        var previous = IntArray(right.length + 1) { it }
        var current = IntArray(right.length + 1)
        for (i in 1..left.length) {
            current[0] = i
            for (j in 1..right.length) {
                val cost = if (left[i - 1] == right[j - 1]) 0 else 1
                current[j] = minOf(current[j - 1] + 1, previous[j] + 1, previous[j - 1] + cost)
            }
            val swap = previous
            previous = current
            current = swap
        }
        return previous[right.length]
    }
}

/**
 * Rounds that work with no key and no network.
 *
 * The arcade is only worth having if it opens when it is needed, and a flaky connection at the
 * wrong minute is exactly when it would not. These are small on purpose — the model's rounds are
 * the main event — but they are real rounds, not placeholders.
 */
object ArcadeOffline {
    val trivia: List<TriviaQuestion> = listOf(
        TriviaQuestion("Which planet has the shortest day in the solar system?", listOf("Mercury", "Jupiter", "Earth", "Mars"), 1, "Jupiter spins once in just under ten hours."),
        TriviaQuestion("How many hearts does an octopus have?", listOf("One", "Two", "Three", "Eight"), 2, "Two pump blood through the gills, one through the rest of the body."),
        TriviaQuestion("What is the only letter that does not appear in any US state name?", listOf("Q", "Z", "X", "J"), 0, "Z sneaks in via Arizona; Q never shows up at all."),
        TriviaQuestion("Which animal can sleep standing up and lie down only briefly?", listOf("Giraffe", "Horse", "Flamingo", "All three"), 3, "Each has its own trick for napping upright."),
        TriviaQuestion("Honey found in Egyptian tombs was still…", listOf("Poisonous", "Edible", "Liquid gold", "Purple"), 1, "Its low moisture and acidity stop bacteria growing."),
        TriviaQuestion("What colour is a polar bear's skin?", listOf("White", "Pink", "Black", "Grey"), 2, "The fur is translucent; the skin underneath soaks up heat."),
        TriviaQuestion("Which country has the most islands?", listOf("Indonesia", "Philippines", "Sweden", "Canada"), 2, "Sweden counts well over 200,000 of them."),
        TriviaQuestion("How long does light from the Sun take to reach Earth?", listOf("8 seconds", "8 minutes", "8 hours", "8 days"), 1, "About 8 minutes and 20 seconds, give or take the orbit."),
        TriviaQuestion("Bananas are botanically classed as…", listOf("Berries", "Herbs", "Drupes", "Nuts"), 0, "Strawberries, meanwhile, are not berries at all."),
        TriviaQuestion("What is the most common bird in the world?", listOf("Pigeon", "Chicken", "Sparrow", "Seagull"), 1, "There are more chickens than any other bird by a wide margin."),
        TriviaQuestion("Which instrument has 88 keys as standard?", listOf("Organ", "Accordion", "Piano", "Harpsichord"), 2, "52 white and 36 black."),
        TriviaQuestion("A group of flamingos is called a…", listOf("Flamboyance", "Parade", "Blush", "Fiesta"), 0, "Which is exactly as it should be."),
        TriviaQuestion("What is the hardest natural substance?", listOf("Quartz", "Diamond", "Titanium", "Granite"), 1, "Diamond scratches everything else in nature."),
        TriviaQuestion("Which sense is most closely linked to memory?", listOf("Sight", "Hearing", "Smell", "Touch"), 2, "Smell is wired almost directly into the brain's memory centres."),
        TriviaQuestion("Roughly how many times does a human heart beat in a day?", listOf("10,000", "50,000", "100,000", "1,000,000"), 2, "Around a hundred thousand, without you asking it to."),
    )

    val emoji: List<EmojiPuzzle> = listOf(
        EmojiPuzzle("🦁👑", "The Lion King", "A film about a cub who would be king", "Film"),
        EmojiPuzzle("🕷️🧑", "Spider-Man", "A superhero with a web habit", "Film"),
        EmojiPuzzle("❄️👸", "Frozen", "Let it go", "Film"),
        EmojiPuzzle("🌧️🐱🐶", "Raining cats and dogs", "A saying about very heavy weather", "Saying"),
        EmojiPuzzle("⏰🐦🪱", "The early bird catches the worm", "A saying about getting up first", "Saying"),
        EmojiPuzzle("🍎📱", "Apple", "A tech company named after fruit", "Brand"),
        EmojiPuzzle("🦈🌊🎬", "Jaws", "You're going to need a bigger boat", "Film"),
        EmojiPuzzle("🧙‍♂️💍🌋", "The Lord of the Rings", "One ring to rule them all", "Film"),
        EmojiPuzzle("🐝🍯", "Honey bee", "It makes something sweet", "Thing"),
        EmojiPuzzle("🌟🔭", "Stargazing", "What you do on a clear night", "Activity"),
        EmojiPuzzle("🔥🧑‍🚒", "Firefighter", "A job with a ladder and a hose", "Job"),
        EmojiPuzzle("🧊🏔️🚢", "Titanic", "A ship that met an iceberg", "Film"),
        EmojiPuzzle("🍝🍽️🇮🇹", "Spaghetti", "A long Italian classic", "Food"),
        EmojiPuzzle("🌈🦄", "Unicorn", "A magical horse", "Creature"),
        EmojiPuzzle("🏴‍☠️💰🗺️", "Treasure map", "X marks the spot", "Thing"),
    )

    /** A round drawn from the bank, varied by [seed] so two rounds in a row do not repeat. */
    fun triviaRound(seed: Long): List<TriviaQuestion> = trivia.shuffled(kotlin.random.Random(seed)).take(ArcadeParser.ROUND_SIZE)

    fun emojiRound(seed: Long): List<EmojiPuzzle> = emoji.shuffled(kotlin.random.Random(seed)).take(ArcadeParser.ROUND_SIZE)

    /** Topics offered on the trivia picker. The first is the default surprise mix. */
    val triviaTopics: List<String> = listOf(
        "Surprise me", "Animals", "Space", "Food", "Music", "Films", "Geography", "History", "Science", "Sport",
    )

    val storyGenres: List<String> = listOf(
        "Cosy mystery", "Space adventure", "Heist", "Haunted house (not scary)", "Fantasy quest", "Time travel",
    )

    /** How many beats a story runs before it must land an ending. */
    const val STORY_BEATS = 5
}
