package com.github.insanusmokrassar.AutoPostBotLikesPlugin.listeners

import com.github.insanusmokrassar.AutoPostBotLikesPlugin.database.LikesPluginMessagesTable
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
import com.github.insanusmokrassar.TelegramBotAPI.utils.extensions.executeUnsafe
import org.joda.time.DateTime
import java.lang.ref.WeakReference

private val commandRegex: Regex = Regex("^/attachLikes$")
private const val commandTemplate: String = "/attachLikes"

internal fun enableDetectLikesAttachmentMessages(
    adminsHolder: AdminsHolder,
    targetChatId: ChatId,
    likesPluginMessagesTable: LikesPluginMessagesTable,
    likesGroupsRegistrator: LikesGroupsRegistrator,
    botWR: WeakReference<RequestsExecutor>
) {
    allMessagesListener.subscribe {
        val message = it.data as? CommonMessage<*> ?: return@subscribe
        val userId = (message as? FromUserMessage) ?.user ?.id ?: return@subscribe

        val forwarded = message.forwarded
        when (forwarded) {
            is ForwardedFromChannelMessage -> {
                val originalMessageId = forwarded.messageId
                if (forwarded.channelChat.id == targetChatId && adminsHolder.contains(userId) && !likesPluginMessagesTable.contains(originalMessageId)) {
                    botWR.get() ?.executeUnsafe(
                        SendMessage(
                            message.chat.id,
                            "Ok, reply this message and send me $commandTemplate",
                            MarkdownParseMode
                        )
                    )
                }
            }
            else -> {
                (message.content as? TextContent) ?.also {
                    val reply = message.replyTo
                    if (commandRegex.matches(it.text) && reply != null && adminsHolder.contains(userId)) {

                        if (reply.chat.id == targetChatId && reply.messageId !in likesPluginMessagesTable) {
                            likesGroupsRegistrator.registerNewLikesGroup(
                                listOf(reply)
                            )
                            botWR.get() ?.executeUnsafe(
                                SendMessage(
                                    message.chat.id,
                                    "Likes was attached (can be showed with delay)"
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}
