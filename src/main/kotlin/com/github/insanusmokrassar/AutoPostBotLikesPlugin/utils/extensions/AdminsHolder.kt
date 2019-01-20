package com.github.insanusmokrassar.AutoPostBotLikesPlugin.utils.extensions

import com.github.insanusmokrassar.TelegramBotAPI.bot.RequestsExecutor
import com.github.insanusmokrassar.TelegramBotAPI.requests.chat.get.GetChatAdministrators
import com.github.insanusmokrassar.TelegramBotAPI.types.ChatId
import com.github.insanusmokrassar.TelegramBotAPI.types.ChatMember.AdministratorChatMember
import com.github.insanusmokrassar.TelegramBotAPI.types.UserId
import org.joda.time.DateTime
import java.lang.ref.WeakReference

class AdminsHolder(
    private val botWR: WeakReference<RequestsExecutor>,
    private val chatId: ChatId,
    private val requestDelay: Long = 3600000L
) {
    private val admins: MutableList<AdministratorChatMember> = ArrayList()
    private val lastRequestTime = DateTime(0)

    suspend fun contains(userId: UserId): Boolean {
        if ((lastRequestTime + requestDelay).isBeforeNow) {
            val adminsResponse = botWR.get() ?.execute(
                GetChatAdministrators(
                    chatId
                )
            ) ?: throw IllegalStateException("Bot was destroyed")
            admins.clear()
            admins.addAll(
                adminsResponse.mapNotNull {
                    it.asChatMember as? AdministratorChatMember
                }
            )
        }
        return admins.firstOrNull { it.user.id == userId } != null
    }
}