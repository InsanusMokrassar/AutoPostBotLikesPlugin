package dev.inmo.AutoPostBotLikesPlugin.utils.extensions

import dev.inmo.AutoPostTelegramBot.utils.NewDefaultCoroutineScope
import dev.inmo.tgbotapi.bot.RequestsExecutor
import dev.inmo.tgbotapi.requests.chat.get.GetChatAdministrators
import dev.inmo.tgbotapi.types.ChatId
import dev.inmo.tgbotapi.types.ChatMember.abstracts.AdministratorChatMember
import dev.inmo.tgbotapi.types.UserId
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
                        it as? AdministratorChatMember
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