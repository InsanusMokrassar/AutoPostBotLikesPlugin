package com.github.insanusmokrassar.AutoPostBotLikesPlugin.models.config

private const val textIdentifier = "\$text"
private const val countIdentifier = "\$count"

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
