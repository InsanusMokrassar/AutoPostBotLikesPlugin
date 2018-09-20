package com.github.insanusmokrassar.AutoPostBotLikesPlugin.reborn.config

private const val textIdentifier = "\$text"
private const val countIdentifier = "\$count"

data class ButtonConfig (
    val text: String = "",
    val id: String = "",
    val format: String = "\$text \$count"
) {
    fun format(count: Int): String {
        return format.replace(textIdentifier, text).replace(countIdentifier, count.toString())
    }
}
