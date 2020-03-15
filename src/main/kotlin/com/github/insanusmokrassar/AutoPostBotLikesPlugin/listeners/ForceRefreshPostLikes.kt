package com.github.insanusmokrassar.AutoPostBotLikesPlugin.listeners

import com.github.insanusmokrassar.AutoPostBotLikesPlugin.database.LikesPluginLikesTable
import com.github.insanusmokrassar.AutoPostBotLikesPlugin.database.LikesPluginRegisteredLikesMessagesTable
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
