package dev.inmo.AutoPostBotLikesPlugin.models.config

import kotlinx.serialization.Serializable

private const val textIdentifier = "\$text"
private const val countIdentifier = "\$count"

@Serializable
data class ButtonConfig (
    val text: String = "",
    val id: String = "",
    val format: String = "\$text \$count",
    val positiveAnswer: AnswerConfig? = null,
    val negativeAnswer: AnswerConfig? = null
) {
    fun format(count: Int): String {
        return format.replace(textIdentifier, text).replace(countIdentifier, count.toString())
    }
}
