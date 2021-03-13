package dev.inmo.AutoPostBotLikesPlugin.listeners

import dev.inmo.AutoPostBotLikesPlugin.database.LikesPluginMessagesTable
import dev.inmo.AutoPostTelegramBot.plugins.publishers.PostIdListPostMessagesTelegramMessages
import dev.inmo.AutoPostTelegramBot.utils.flow.collectWithErrors
import dev.inmo.tgbotapi.bot.RequestsExecutor
import dev.inmo.tgbotapi.requests.send.SendTextMessage
import dev.inmo.tgbotapi.types.ChatId
import dev.inmo.tgbotapi.types.MessageIdentifier
import dev.inmo.tgbotapi.types.ParseMode.MarkdownParseMode
import dev.inmo.tgbotapi.types.message.abstracts.MediaGroupMessage
import dev.inmo.tgbotapi.types.message.abstracts.Message
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.flow.asFlow
import java.lang.ref.WeakReference

class LikesGroupsRegistrator(
    channel: BroadcastChannel<PostIdListPostMessagesTelegramMessages>,
    private val likesMessagesTable: LikesPluginMessagesTable,
    private val chatId: ChatId,
    private val separateAlways: Boolean,
    private val separatedText: String,
    private val botWR: WeakReference<RequestsExecutor>,
    scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {
    init {
        scope.launch {
            channel.asFlow().collectWithErrors { (_, messagesPairs) ->
                registerNewLikesGroup(messagesPairs)
            }
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

        val messageIdToRegister = if (separateAlways || (lastMessage is MediaGroupMessage<*>)) {
            registerSeparatedLike(firstMessage.messageId)
        } else {
            registerAttachedLike(lastMessage.messageId)
        }

        return messages.map {
            it.messageId
        }.let {
            likesMessagesTable.registerMessagesForLikeGroup(
                messageIdToRegister,
                it,
                messages.mapNotNull { message ->
                    (message as? MediaGroupMessage<*>) ?.let { asMediaGroupMessage ->
                        asMediaGroupMessage.messageId to asMediaGroupMessage.mediaGroupId
                    }
                }.toMap()
            )
        }
    }

    suspend fun registerSeparatedLike(
        messageId: MessageIdentifier
    ): MessageIdentifier {
        return likesMessagesTable.getLikesGroupId(messageId) ?: let {
            val bot = botWR.get() ?: throw IllegalStateException("Bot was collected by GC")
            val registeredMessage = bot.execute(
                SendTextMessage(
                    chatId,
                    separatedText,
                    MarkdownParseMode,
                    replyToMessageId = messageId
                )
            ).messageId
            val registered = likesMessagesTable.registerMessagesForLikeGroup(
                registeredMessage,
                listOf(messageId),
                emptyMap()
            )
            if (registered) {
                registeredMessage
            } else {
                throw IllegalStateException("Can't register message identifier")
            }
        }
    }

    fun registerAttachedLike(
        messageId: MessageIdentifier
    ): MessageIdentifier {
        return likesMessagesTable.getLikesGroupId(messageId) ?: messageId.also {
            likesMessagesTable.registerMessagesForLikeGroup(messageId, listOf(messageId), emptyMap())
        }
    }
}