package com.github.insanusmokrassar.AutoPostBotLikesPlugin.models

import org.joda.time.DateTime

data class UserMark(
    val postId: Int,
    val chatId: Long,
    val mark: String,
    val dateTime: DateTime = DateTime.now(),
    val id: Int? = null
)
