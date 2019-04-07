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
import java.lang.ref.WeakReference

private val attachRegex: Regex = Regex("^/attachLikes$")
private val attachSeparatedRegex: Regex = Regex("^/attachSeparatedLikes$")
private const val attachTemplate: String = "/attachLikes"
private const val attachSeparatedTemplate: String = "/attachSeparatedLikes"

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

        (message.forwarded as? ForwardedFromChannelMessage) ?.let { forwarded ->

            val originalMessageId = forwarded.messageId
            if (forwarded.channelChat.id == targetChatId && adminsHolder.contains(userId) && !likesPluginMessagesTable.contains(originalMessageId)) {
                botWR.get() ?.executeUnsafe(
                    SendMessage(
                        message.chat.id,
                        "Ok, reply this message and send me `$attachTemplate` OR `$attachSeparatedTemplate` to attach with separated message",
                        MarkdownParseMode,
                        replyToMessageId = message.messageId
                    )
                )
            }

        } ?: (message.content as? TextContent) ?.let {

            val reply = message.replyTo as? CommonMessage<*> ?: return@let
            if (!adminsHolder.contains(userId)) {
                return@let
            }
            val text = it.text
            val realForwarded = reply.forwarded as? ForwardedFromChannelMessage ?: return@let

            if (realForwarded.channelChat.id == targetChatId && realForwarded.messageId !in likesPluginMessagesTable) {
                when {
                    attachRegex.matches(text) -> {
                        likesGroupsRegistrator.registerAttachedLike(
                            realForwarded.messageId
                        )
                        botWR.get() ?.executeUnsafe(
                            SendMessage(
                                message.chat.id,
                                "Likes was attached (can be shown with delay)"
                            )
                        )
                    }
                    attachSeparatedRegex.matches(text) -> {
                        likesGroupsRegistrator.registerSeparatedLike(
                            realForwarded.messageId
                        )
                        botWR.get() ?.executeUnsafe(
                            SendMessage(
                                message.chat.id,
                                "Likes was attached by separated message (can be shown with delay)"
                            )
                        )
                    }
                }
            }
        }
    }
}
