package com.github.insanusmokrassar.AutoPostBotLikesPlugin.models.config

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class LikePluginConfig(
    val buttons: List<ButtonConfig> = emptyList(),
    private val groups: List<GroupConfig> = emptyList(),
    val separateAlways: Boolean = false,
    val separatedText: String = "Like? :)",
    val debounceDelay: Long = 1000
) {
    @Transient
    private val realGroups: List<GroupConfig> by lazy {
        if (groups.isEmpty()) {
            listOf(
                GroupConfig(
                    items = buttons.map { it.id }
                )
            )
        } else {
            groups
        }
    }

    @Transient
    val adaptedGroups: List<Group> by lazy {
        realGroups.map {
            group ->
            Group(
                group.radio,
                buttons.filter {
                    it.id in group.items
                }
            )
        }
    }
}
