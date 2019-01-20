package com.github.insanusmokrassar.AutoPostBotLikesPlugin.models.config

import kotlinx.serialization.Serializable

@Serializable
data class Group(
    val isRadio: Boolean,
    val items: List<ButtonConfig>
) {
    fun other(buttonConfig: ButtonConfig): List<ButtonConfig> {
        return items.minus(buttonConfig)
    }
}
