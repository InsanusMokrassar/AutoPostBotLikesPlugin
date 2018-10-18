package com.github.insanusmokrassar.AutoPostBotLikesPlugin.utils.extensions

import com.github.insanusmokrassar.AutoPostTelegramBot.utils.extensions.executeBlocking
import com.pengrad.telegrambot.TelegramBot
import com.pengrad.telegrambot.model.ChatMember
import com.pengrad.telegrambot.request.GetChatAdministrators
import org.joda.time.DateTime
import java.lang.ref.WeakReference

class AdminsHolder(
    private val botWR: WeakReference<TelegramBot>,
    private val chatId: Long,
    private val requestDelay: Long = 3600000L
) {
    private val admins: MutableList<ChatMember> = ArrayList()
    private val lastRequestTime = DateTime(0)

    suspend fun contains(userId: Long): Boolean {
        if ((lastRequestTime + requestDelay).isBeforeNow) {
            val adminsResponse = botWR.get() ?.executeBlocking(
                GetChatAdministrators(
                    chatId
                )
            ) ?: throw IllegalStateException("Bot was destroyed")
            admins.clear()
            admins.addAll(
                adminsResponse.administrators()
            )
        }
        return admins.firstOrNull { it.user().id().toLong() == userId } != null
    }
}