package com.github.insanusmokrassar.AutoPostBotLikesPlugin.utils.extensions

import com.github.insanusmokrassar.AutoPostTelegramBot.utils.NewDefaultCoroutineScope
import com.github.insanusmokrassar.TelegramBotAPI.bot.RequestsExecutor
import com.github.insanusmokrassar.TelegramBotAPI.requests.chat.get.GetChatAdministrators
import com.github.insanusmokrassar.TelegramBotAPI.types.ChatId
import com.github.insanusmokrassar.TelegramBotAPI.types.ChatMember.abstracts.AdministratorChatMember
import com.github.insanusmokrassar.TelegramBotAPI.types.UserId
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import org.joda.time.DateTime
import java.lang.ref.WeakReference
import kotlin.coroutines.Continuation
import kotlin.coroutines.suspendCoroutine

class AdminsHolder(
    private val botWR: WeakReference<RequestsExecutor>,
    private val chatId: ChatId,
    private val requestDelay: Long = 3600000L
) {
    private val admins: MutableList<AdministratorChatMember> = ArrayList()
    private val lastRequestTime = DateTime(0)

    private val adminsHolderScope = NewDefaultCoroutineScope(2)
    private val adminsChangesChannel = Channel<Pair<UserId, Continuation<Boolean>>>(Channel.UNLIMITED)
    private val job = adminsHolderScope.launch {
        for (pair in adminsChangesChannel) {
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
            pair.second.resumeWith(Result.success(admins.firstOrNull { it.user.id == pair.first } != null))
        }
    }

    suspend fun contains(userId: UserId): Boolean {
        return suspendCoroutine {
            adminsHolderScope.launch { adminsChangesChannel.send(userId to it) }
        }
    }
}