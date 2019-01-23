package com.github.insanusmokrassar.AutoPostBotLikesPlugin.models

import com.github.insanusmokrassar.TelegramBotAPI.types.MessageIdentifier

data class Mark(
    val userId: Long,
    val messageId: MessageIdentifier,
    val buttonId: String
)
