package com.github.insanusmokrassar.AutoPostBotLikesPlugin.models.config

import kotlinx.serialization.Optional
import kotlinx.serialization.Serializable

private const val textIdentifier = "\$text"
private const val countIdentifier = "\$count"

@Serializable
data class ButtonConfig (
    @Optional
    val text: String = "",
    @Optional
    val id: String = "",
    @Optional
    val format: String = "\$text \$count",
    @Optional
    val positiveAnswer: AnswerConfig? = null,
    @Optional
    val negativeAnswer: AnswerConfig? = null
) {
    fun format(count: Int): String {
        return format.replace(textIdentifier, text).replace(countIdentifier, count.toString())
    }
}
