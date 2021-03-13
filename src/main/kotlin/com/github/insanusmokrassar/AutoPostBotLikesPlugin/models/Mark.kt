package dev.inmo.AutoPostBotLikesPlugin.models

import dev.inmo.tgbotapi.types.MessageIdentifier

data class Mark(
    val userId: Long,
    val messageId: MessageIdentifier,
    val buttonId: String
)
