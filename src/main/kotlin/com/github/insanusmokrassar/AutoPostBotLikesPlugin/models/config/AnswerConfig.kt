package com.github.insanusmokrassar.AutoPostBotLikesPlugin.models.config

import kotlinx.serialization.Optional
import kotlinx.serialization.Serializable

@Serializable
data class AnswerConfig (
    @Optional
    val text: String = "",
    @Optional
    val alert: Boolean = false
)