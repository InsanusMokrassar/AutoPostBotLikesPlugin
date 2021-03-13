package dev.inmo.AutoPostBotLikesPlugin.models

import dev.inmo.tgbotapi.types.MessageIdentifier

data class ButtonMark(
    val messageId: MessageIdentifier,
    val buttonId: String,
    val count: Int
)
