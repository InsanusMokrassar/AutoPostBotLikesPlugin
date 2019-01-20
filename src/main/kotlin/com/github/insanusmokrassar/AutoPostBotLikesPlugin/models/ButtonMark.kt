package com.github.insanusmokrassar.AutoPostBotLikesPlugin.models

import com.github.insanusmokrassar.TelegramBotAPI.types.MessageIdentifier

data class ButtonMark(
    val messageId: MessageIdentifier,
    val buttonId: String,
    val count: Int
)
