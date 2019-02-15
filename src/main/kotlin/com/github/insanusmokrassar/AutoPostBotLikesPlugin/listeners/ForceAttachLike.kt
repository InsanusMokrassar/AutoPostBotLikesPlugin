package com.github.insanusmokrassar.AutoPostBotLikesPlugin.listeners

import com.github.insanusmokrassar.AutoPostBotLikesPlugin.database.LikesPluginRegisteredLikesMessagesTable
import com.github.insanusmokrassar.AutoPostBotLikesPlugin.utils.extensions.AdminsHolder
import com.github.insanusmokrassar.AutoPostTelegramBot.allMessagesListener
import com.github.insanusmokrassar.AutoPostTelegramBot.utils.extensions.subscribe
import com.github.insanusmokrassar.TelegramBotAPI.bot.RequestsExecutor
import com.github.insanusmokrassar.TelegramBotAPI.requests.send.SendMessage
import com.github.insanusmokrassar.TelegramBotAPI.types.ChatId
import com.github.insanusmokrassar.TelegramBotAPI.types.ParseMode.MarkdownParseMode
import com.github.insanusmokrassar.TelegramBotAPI.types.message.ForwardedFromChannelMessage
import com.github.insanusmokrassar.TelegramBotAPI.types.message.abstracts.CommonMessage
import com.github.insanusmokrassar.TelegramBotAPI.types.message.abstracts.FromUserMessage
import com.github.insanusmokrassar.TelegramBotAPI.types.message.content.TextContent
import com.github.insanusmokrassar.TelegramBotAPI.utils.extensions.executeAsync
import org.joda.time.DateTime
import java.lang.ref.WeakReference

private val commandRegex: Regex = Regex("^/attachTargetLike [\\d]+$")
private const val commandTemplate: String = "/attachTargetLike %d"

internal fun enableDetectLikesAttachmentMessages(
    adminsHolder: AdminsHolder,
    targetChatId: ChatId,
    likesPluginRegisteredLikesMessagesTable: LikesPluginRegisteredLikesMessagesTable,
    botWR: WeakReference<RequestsExecutor>
) {
    allMessagesListener.subscribe {
        val message = it.data as? CommonMessage<*> ?: return@subscribe
        val userId = (message as? FromUserMessage) ?.user ?.id ?: return@subscribe

        val forwarded = message.forwarded
        when (forwarded) {
            is ForwardedFromChannelMessage -> {
                val originalMessageId = forwarded.messageId
                if (forwarded.channelChat.id == targetChatId && adminsHolder.contains(userId) && !likesPluginRegisteredLikesMessagesTable.contains(originalMessageId)) {
                    botWR.get() ?.executeAsync(
                        SendMessage(
                            message.chat.id,
                            "Ok, send me `${commandTemplate.format(originalMessageId)}` for attach post likes",
                            MarkdownParseMode
                        )
                    )
                }
            }
            else -> {
                (message.content as? TextContent) ?.also {
                    if (commandRegex.matches(it.text) && adminsHolder.contains(userId)) {
                        val messageId = it.text.split(" ")[1].toLong()

                        if (messageId !in likesPluginRegisteredLikesMessagesTable) {
                            likesPluginRegisteredLikesMessagesTable.registerMessageId(
                                messageId,
                                DateTime.now()
                            ).also {
                                if (it) {
                                    botWR.get() ?.executeAsync(
                                        SendMessage(
                                            message.chat.id,
                                            "Likes was attached (can be showed with delay)"
                                        )
                                    )
                                } else {
                                    botWR.get() ?.executeAsync(
                                        SendMessage(
                                            message.chat.id,
                                            "Likes was not attached (can be already attached)"
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
