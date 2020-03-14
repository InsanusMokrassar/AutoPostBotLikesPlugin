package com.github.insanusmokrassar.AutoPostBotLikesPlugin.listeners

import com.github.insanusmokrassar.AutoPostBotLikesPlugin.database.LikesPluginMessagesTable
import com.github.insanusmokrassar.AutoPostBotLikesPlugin.utils.extensions.AdminsHolder
import com.github.insanusmokrassar.AutoPostTelegramBot.flowFilter
import com.github.insanusmokrassar.AutoPostTelegramBot.utils.flow.collectWithErrors
import com.github.insanusmokrassar.TelegramBotAPI.bot.RequestsExecutor
import com.github.insanusmokrassar.TelegramBotAPI.requests.send.SendTextMessage
import com.github.insanusmokrassar.TelegramBotAPI.types.ChatId
import com.github.insanusmokrassar.TelegramBotAPI.types.ParseMode.MarkdownParseMode
import com.github.insanusmokrassar.TelegramBotAPI.types.message.ForwardFromChannelInfo
import com.github.insanusmokrassar.TelegramBotAPI.types.message.abstracts.CommonMessage
import com.github.insanusmokrassar.TelegramBotAPI.types.message.abstracts.FromUserMessage
import com.github.insanusmokrassar.TelegramBotAPI.types.message.content.TextContent
import com.github.insanusmokrassar.TelegramBotAPI.types.update.abstracts.BaseMessageUpdate
import com.github.insanusmokrassar.TelegramBotAPI.utils.extensions.executeUnsafe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

private val attachRegex: Regex = Regex("^/attachLikes$")
private val attachSeparatedRegex: Regex = Regex("^/attachSeparatedLikes$")
private const val attachTemplate: String = "/attachLikes"
private const val attachSeparatedTemplate: String = "/attachSeparatedLikes"

internal fun CoroutineScope.enableDetectLikesAttachmentMessages(
    adminsHolder: AdminsHolder,
    targetChatId: ChatId,
    likesPluginMessagesTable: LikesPluginMessagesTable,
    likesGroupsRegistrator: LikesGroupsRegistrator,
    botWR: WeakReference<RequestsExecutor>
) {
    val receiver: suspend (BaseMessageUpdate) -> Unit = {
        val message = it.data as? CommonMessage<*>
        (message as? FromUserMessage) ?.user ?.id ?.let { userId ->

            (message.forwardInfo as? ForwardFromChannelInfo) ?.let { forwarded ->

                val originalMessageId = forwarded.messageId
                if (forwarded.channelChat.id == targetChatId && adminsHolder.contains(userId) && !likesPluginMessagesTable.contains(originalMessageId)) {
                    botWR.get() ?.executeUnsafe(
                        SendTextMessage(
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
                val realForwarded = reply.forwardInfo as? ForwardFromChannelInfo ?: return@let

                if (realForwarded.channelChat.id == targetChatId && realForwarded.messageId !in likesPluginMessagesTable) {
                    when {
                        attachRegex.matches(text) -> {
                            likesGroupsRegistrator.registerAttachedLike(
                                realForwarded.messageId
                            )
                            botWR.get() ?.executeUnsafe(
                                SendTextMessage(
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
                                SendTextMessage(
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

    launch {
        flowFilter.messageFlow.collectWithErrors(receiver)
    }
    launch {
        flowFilter.channelPostFlow.collectWithErrors(receiver)
    }
}
