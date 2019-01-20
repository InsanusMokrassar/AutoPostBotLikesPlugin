package com.github.insanusmokrassar.AutoPostBotLikesPlugin.models.config

import kotlinx.serialization.Optional
import kotlinx.serialization.Serializable

@Serializable
data class GroupConfig(
    @Optional
    val radio: Boolean = true,
    @Optional
    val items: List<String> = emptyList()
)
