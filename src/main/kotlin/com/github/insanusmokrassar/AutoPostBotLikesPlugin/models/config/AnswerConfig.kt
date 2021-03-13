package dev.inmo.AutoPostBotLikesPlugin.models.config

import kotlinx.serialization.Serializable

@Serializable
data class AnswerConfig (
    val text: String = "",
    val alert: Boolean = false
)