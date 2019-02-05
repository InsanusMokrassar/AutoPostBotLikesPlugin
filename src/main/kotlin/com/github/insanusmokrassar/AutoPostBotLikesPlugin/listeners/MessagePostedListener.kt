package com.github.insanusmokrassar.AutoPostBotLikesPlugin.listeners

import com.github.insanusmokrassar.AutoPostBotLikesPlugin.database.LikesPluginRegisteredLikesMessagesTable
import com.github.insanusmokrassar.AutoPostTelegramBot.plugins.publishers.PostIdListPostMessagesTelegramMessages
import com.github.insanusmokrassar.AutoPostTelegramBot.utils.extensions.subscribe
import com.github.insanusmokrassar.AutoPostTelegramBot.utils.extensions.subscribeChecking
import com.github.insanusmokrassar.TelegramBotAPI.bot.RequestsExecutor
import com.github.insanusmokrassar.TelegramBotAPI.requests.send.SendMessage
import com.github.insanusmokrassar.TelegramBotAPI.types.ChatId
import com.github.insanusmokrassar.TelegramBotAPI.types.ParseMode.MarkdownParseMode
import com.github.insanusmokrassar.TelegramBotAPI.types.message.abstracts.MediaGroupMessage
import com.github.insanusmokrassar.TelegramBotAPI.utils.extensions.executeAsync
import kotlinx.coroutines.channels.BroadcastChannel
import org.joda.time.DateTime
import java.lang.ref.WeakReference
import java.util.concurrent.TimeUnit

class MessagePostedListener(
    channel: BroadcastChannel<PostIdListPostMessagesTelegramMessages>,
    private val likesPluginRegisteredLikesMessagesTable: LikesPluginRegisteredLikesMessagesTable,
    private val chatId: ChatId,
    private val separateAlways: Boolean,
    private val separatedText: String,
    private val botWR: WeakReference<RequestsExecutor>
) {
    init {
        channel.subscribe { (_, messagesPairs) ->
            val bot = botWR.get() ?: return@subscribe
            val firstMessage = messagesPairs.values.minBy {
                it.messageId
            } ?: return@subscribe
            val lastMessage = messagesPairs.values.maxBy {
                it.messageId
            } ?: return@subscribe

            if (separateAlways || (lastMessage is MediaGroupMessage)) {
                val result = bot.execute(
                    SendMessage(
                        chatId,
                        separatedText,
                        MarkdownParseMode,
                        replyToMessageId = firstMessage.messageId
                    )
                )
                val sendResponse = result.asMessage
                likesPluginRegisteredLikesMessagesTable.registerMessageId(
                    sendResponse.messageId,
                    sendResponse.date
                )
            } else {
                likesPluginRegisteredLikesMessagesTable.registerMessageId(
                    lastMessage.messageId,
                    lastMessage.date
                )
            }
        }
    }
}