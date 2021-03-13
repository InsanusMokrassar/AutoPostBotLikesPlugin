package dev.inmo.AutoPostBotLikesPlugin.models.config

import kotlinx.serialization.Serializable

@Serializable
data class GroupConfig(
    val radio: Boolean = true,
    val items: List<String> = emptyList()
)
