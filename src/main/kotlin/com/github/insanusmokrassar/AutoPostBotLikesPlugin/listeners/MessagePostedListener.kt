package com.github.insanusmokrassar.AutoPostBotLikesPlugin.listeners

import com.github.insanusmokrassar.AutoPostBotLikesPlugin.database.LikesPluginRegisteredLikesMessagesTable
import com.github.insanusmokrassar.AutoPostTelegramBot.plugins.publishers.PostIdListPostMessagesTelegramMessages
import com.github.insanusmokrassar.AutoPostTelegramBot.utils.extensions.executeAsync
import com.github.insanusmokrassar.AutoPostTelegramBot.utils.extensions.subscribeChecking
import com.pengrad.telegrambot.TelegramBot
import com.pengrad.telegrambot.model.request.ParseMode
import com.pengrad.telegrambot.request.SendMessage
import kotlinx.coroutines.experimental.channels.BroadcastChannel
import org.joda.time.DateTime
import java.lang.ref.WeakReference
import java.util.concurrent.TimeUnit

class MessagePostedListener(
    channel: BroadcastChannel<PostIdListPostMessagesTelegramMessages>,
    private val likesPluginRegisteredLikesMessagesTable: LikesPluginRegisteredLikesMessagesTable,
    private val chatId: Long,
    private val separateAlways: Boolean,
    private val separatedText: String,
    private val botWR: WeakReference<TelegramBot>
) {
    init {
        channel.subscribeChecking {
            val bot = botWR.get() ?: return@subscribeChecking false
            val firstMessage = it.second.values.minBy {
                it.messageId()
            } ?: return@subscribeChecking true
            val lastMessage = it.second.values.maxBy {
                it.messageId()
            } ?: return@subscribeChecking true

            if (separateAlways || lastMessage.mediaGroupId() != null) {
                bot.executeAsync(
                    SendMessage(
                        chatId,
                        separatedText
                    ).parseMode(
                        ParseMode.Markdown
                    ).replyToMessageId(
                        firstMessage.messageId()
                    ),
                    onResponse = {
                        _, sendResponse ->
                        likesPluginRegisteredLikesMessagesTable.registerMessageId(
                            sendResponse.message().messageId(),
                            sendResponse ?.message() ?.date() ?.toLong() ?.let {
                                DateTime(
                                    TimeUnit.SECONDS.toMillis(
                                        it
                                    )
                                )
                            } ?: DateTime.now()
                        )
                    }
                )
            } else {
                likesPluginRegisteredLikesMessagesTable.registerMessageId(
                    lastMessage.messageId(),
                    lastMessage.date() ?.toLong() ?.let {
                        DateTime(
                            TimeUnit.SECONDS.toMillis(
                                it
                            )
                        )
                    } ?: DateTime.now()
                )
            }

            true
        }
    }
}