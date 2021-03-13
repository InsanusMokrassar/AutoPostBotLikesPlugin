package dev.inmo.AutoPostBotLikesPlugin.listeners

import dev.inmo.AutoPostBotLikesPlugin.database.LikesPluginMessagesTable
import dev.inmo.AutoPostBotLikesPlugin.utils.extensions.AdminsHolder
import dev.inmo.AutoPostTelegramBot.flowFilter
import dev.inmo.AutoPostTelegramBot.utils.flow.collectWithErrors
import dev.inmo.tgbotapi.bot.RequestsExecutor
import dev.inmo.tgbotapi.extensions.utils.shortcuts.executeUnsafe
import dev.inmo.tgbotapi.requests.send.SendTextMessage
import dev.inmo.tgbotapi.types.ChatId
import dev.inmo.tgbotapi.types.ParseMode.MarkdownParseMode
import dev.inmo.tgbotapi.types.message.ForwardFromChannelInfo
import dev.inmo.tgbotapi.types.message.abstracts.CommonMessage
import dev.inmo.tgbotapi.types.message.abstracts.FromUserMessage
import dev.inmo.tgbotapi.types.message.content.TextContent
import dev.inmo.tgbotapi.types.update.abstracts.BaseMessageUpdate
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
