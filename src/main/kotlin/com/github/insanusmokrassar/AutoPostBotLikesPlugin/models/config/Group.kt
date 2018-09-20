package com.github.insanusmokrassar.AutoPostBotLikesPlugin.models.config

data class Group(
    val isRadio: Boolean,
    val items: List<ButtonConfig>
) {
    fun other(buttonConfig: ButtonConfig): List<ButtonConfig> {
        return items.minus(buttonConfig)
    }
}
