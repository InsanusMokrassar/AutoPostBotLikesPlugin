package com.github.insanusmokrassar.AutoPostBotLikesPlugin.listeners

import com.github.insanusmokrassar.AutoPostBotLikesPlugin.database.LikesPluginMessagesTable
import com.github.insanusmokrassar.AutoPostTelegramBot.plugins.publishers.PostIdListPostMessagesTelegramMessages
import com.github.insanusmokrassar.AutoPostTelegramBot.utils.extensions.subscribe
import com.github.insanusmokrassar.TelegramBotAPI.bot.RequestsExecutor
import com.github.insanusmokrassar.TelegramBotAPI.requests.send.SendMessage
import com.github.insanusmokrassar.TelegramBotAPI.types.ChatId
import com.github.insanusmokrassar.TelegramBotAPI.types.MessageIdentifier
import com.github.insanusmokrassar.TelegramBotAPI.types.ParseMode.MarkdownParseMode
import com.github.insanusmokrassar.TelegramBotAPI.types.message.abstracts.MediaGroupMessage
import com.github.insanusmokrassar.TelegramBotAPI.types.message.abstracts.Message
import kotlinx.coroutines.channels.BroadcastChannel
import java.lang.ref.WeakReference

class LikesGroupsRegistrator(
    channel: BroadcastChannel<PostIdListPostMessagesTelegramMessages>,
    private val likesMessagesTable: LikesPluginMessagesTable,
    private val chatId: ChatId,
    private val separateAlways: Boolean,
    private val separatedText: String,
    private val botWR: WeakReference<RequestsExecutor>
) {
    init {
        channel.subscribe { (_, messagesPairs) ->
            registerNewLikesGroup(messagesPairs)
        }
    }

    suspend fun registerNewLikesGroup(
        messages: List<Message>
    ): Boolean {
        val firstMessage = messages.minBy {
            it.messageId
        } ?: return false
        val lastMessage = messages.maxBy {
            it.messageId
        } ?: return false

        val messageIdToRegister = if (separateAlways || (lastMessage is MediaGroupMessage)) {
            registerSeparatedLike(firstMessage.messageId)
        } else {
            registerAttachedLike(lastMessage.messageId)
        }

        return messages.map {
            it.messageId
        }.let {
            likesMessagesTable.registerMessagesForLikeGroup(
                messageIdToRegister,
                it
            )
        }
    }

    suspend fun registerSeparatedLike(
        messageId: MessageIdentifier
    ): MessageIdentifier {
        return likesMessagesTable.getLikesGroupId(messageId) ?: let {
            val bot = botWR.get() ?: throw IllegalStateException("Bot was collected by GC")
            bot.execute(
                SendMessage(
                    chatId,
                    separatedText,
                    MarkdownParseMode,
                    replyToMessageId = messageId
                )
            ).asMessage.messageId
        }
    }

    suspend fun registerAttachedLike(
        messageId: MessageIdentifier
    ): MessageIdentifier {
        return likesMessagesTable.getLikesGroupId(messageId) ?: messageId.also {
            likesMessagesTable.registerMessagesForLikeGroup(messageId, listOf(messageId))
        }
    }
}