package com.gravijet.daydrop.data.model

import kotlinx.serialization.Serializable

/**
 * The eight card flavours a daily drop can be built from. The order here is the
 * order the feed prefers when it lays a day out.
 */
enum class DropType(val label: String, val emoji: String) {
    TODAY_IS("Heute ist", "📅"),
    HISTORY("Heute vor", "⏳"),
    FACT("Fact of the Day", "🤯"),
    DID_YOU_KNOW("Wusstest du?", "🌍"),
    SCIENCE("Science Drop", "🔬"),
    POP_CULTURE("Pop Culture", "🎬"),
    QUIZ("Mini-Frage", "🧠"),
    RANDOM("Random Drop", "🎲")
}

@Serializable
data class Quiz(
    val question: String,
    val options: List<String>,
    val answerIndex: Int,
    val explanation: String
)

/**
 * One swipeable card. [id] is stable across days for the same piece of content,
 * which is what makes favourites survive a restart.
 */
/**
 * Which of the five daily slots a card fills.
 *
 * [HISTORY] and [DAILY] are provisional offline: the bundled entry holds the
 * place until Wikipedia answers, and only what survives that swap counts as
 * shown. The other three are final the moment they are built.
 */
object Slot {
    const val TODAY = "today"
    const val HISTORY = "history"
    const val DAILY = "daily"
    const val FOR_YOU = "you"
    const val QUIZ = "quiz"

    /** Slots that [com.gravijet.daydrop.domain.DropGenerator.enrich] may replace. */
    val PROVISIONAL = setOf(HISTORY, DAILY)
}

@Serializable
data class Drop(
    val id: String,
    val type: DropType,
    val eyebrow: String,
    val title: String,
    val body: String,
    val imageUrl: String? = null,
    val sourceUrl: String? = null,
    val sourceLabel: String? = null,
    val quiz: Quiz? = null,
    val topics: List<String> = emptyList(),
    val personalised: Boolean = false,
    val slot: String = "",
    val savedAt: Long = 0L
)

/** A topic the user can subscribe to during onboarding. */
data class Interest(
    val id: String,
    val label: String,
    val emoji: String
) {
    companion object {
        val ALL = listOf(
            Interest("history", "Geschichte", "🏛️"),
            Interest("science", "Wissenschaft", "🔬"),
            Interest("space", "Weltraum", "🚀"),
            Interest("animals", "Tiere", "🦊"),
            Interest("nature", "Natur", "🌿"),
            Interest("body", "Körper", "🫀"),
            Interest("tech", "Technik", "💡"),
            Interest("geography", "Geografie", "🗺️"),
            Interest("sports", "Sport", "⚽"),
            Interest("gaming", "Gaming", "🎮"),
            Interest("music", "Musik", "🎵"),
            Interest("movies", "Filme & Serien", "🎬"),
            Interest("food", "Essen", "🍕"),
            Interest("language", "Sprache", "🗣️"),
            Interest("weird", "Weird Facts", "🥸")
        )

        fun labelFor(id: String): String = ALL.firstOrNull { it.id == id }?.label ?: id
    }
}
