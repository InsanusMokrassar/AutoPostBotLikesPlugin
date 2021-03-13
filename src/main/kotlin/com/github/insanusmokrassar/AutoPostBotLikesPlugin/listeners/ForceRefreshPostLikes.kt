package dev.inmo.AutoPostBotLikesPlugin.listeners

import dev.inmo.AutoPostBotLikesPlugin.database.LikesPluginLikesTable
import dev.inmo.AutoPostBotLikesPlugin.database.LikesPluginRegisteredLikesMessagesTable
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

private val commandRegex: Regex = Regex("^/refreshTargetLike [\\d]+$")
private const val commandTemplate: String = "/refreshTargetLike %d"

internal fun CoroutineScope.enableDetectLikesRefreshMessages(
    adminsHolder: AdminsHolder,
    targetChatId: ChatId,
    likesPluginLikesTable: LikesPluginLikesTable,
    likesPluginRegisteredLikesMessagesTable: LikesPluginRegisteredLikesMessagesTable,
    botWR: WeakReference<RequestsExecutor>
) {
    val updateChannel = likesPluginLikesTable.messageButtonsUpdatedChannel

    val receiver: suspend (BaseMessageUpdate) -> Unit = {
        val message = it.data as? CommonMessage<*>
        (message as? FromUserMessage) ?.user ?.id ?.let { userId ->
            (message.forwardInfo as? ForwardFromChannelInfo) ?.let { forwardInfo ->
                val originalMessageId = forwardInfo.messageId
                if (forwardInfo.channelChat.id == targetChatId && adminsHolder.contains(userId) && likesPluginRegisteredLikesMessagesTable.contains(originalMessageId)) {
                    botWR.get() ?.executeUnsafe(
                        SendTextMessage(
                            message.chat.id,
                            "Send me `${commandTemplate.format(originalMessageId)}` for force post likes update",
                            MarkdownParseMode
                        )
                    )
                }
            } ?: (message.content as? TextContent) ?.also { content ->
                if (commandRegex.matches(content.text) && adminsHolder.contains(userId)) {
                    val messageId = content.text.split(" ")[1].toLong()

                    if (messageId in likesPluginRegisteredLikesMessagesTable) {
                        updateChannel.send(messageId)

                        botWR.get() ?.executeUnsafe(
                            SendTextMessage(
                                message.chat.id,
                                "Likes was updated (can be showed with delay)"
                            )
                        )
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
