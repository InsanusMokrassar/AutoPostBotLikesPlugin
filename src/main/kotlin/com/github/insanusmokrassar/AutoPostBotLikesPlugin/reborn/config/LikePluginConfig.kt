package com.github.insanusmokrassar.AutoPostBotLikesPlugin.reborn.config

data class LikePluginConfig(
    val buttons: List<ButtonConfig> = emptyList(),
    val groups: List<GroupConfig> = emptyList()
) {
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
