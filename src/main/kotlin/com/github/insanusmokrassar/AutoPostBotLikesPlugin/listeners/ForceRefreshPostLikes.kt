package com.github.insanusmokrassar.AutoPostBotLikesPlugin.listeners

import com.github.insanusmokrassar.AutoPostBotLikesPlugin.database.LikesPluginLikesTable
import com.github.insanusmokrassar.AutoPostBotLikesPlugin.database.LikesPluginRegisteredLikesMessagesTable
import com.github.insanusmokrassar.AutoPostBotLikesPlugin.utils.extensions.AdminsHolder
import com.github.insanusmokrassar.AutoPostTelegramBot.messagesListener
import com.github.insanusmokrassar.AutoPostTelegramBot.realMessagesListener
import com.github.insanusmokrassar.AutoPostTelegramBot.utils.commands.Command
import com.github.insanusmokrassar.AutoPostTelegramBot.utils.extensions.executeAsync
import com.github.insanusmokrassar.AutoPostTelegramBot.utils.extensions.subscribe
import com.pengrad.telegrambot.TelegramBot
import com.pengrad.telegrambot.model.Message
import com.pengrad.telegrambot.request.SendMessage
import org.joda.time.DateTime
import java.lang.ref.WeakReference

private val commandRegex: Regex = Regex("^/refreshTargetLike [\\d]+$")
private const val commandTemplate: String = "/refreshTargetLike %d"

internal fun enableDetectLikesRefreshMessages(
    adminsHolder: AdminsHolder,
    targetChatId: Long,
    likesPluginLikesTable: LikesPluginLikesTable,
    likesPluginRegisteredLikesMessagesTable: LikesPluginRegisteredLikesMessagesTable,
    botWR: WeakReference<TelegramBot>
) {
    val updateChannel = likesPluginLikesTable.messageButtonsUpdatedChannel

    realMessagesListener.broadcastChannel.subscribe {
        (_, message) ->
        val userId = message.from().id().toLong()
        message.forwardFromChat() ?.let {
            forwardFromChat ->
            if (forwardFromChat.id() == targetChatId && adminsHolder.contains(userId)) {
                botWR.get() ?.executeAsync(
                    SendMessage(
                        message.chat().id(),
                        "Send me `${commandTemplate.format(message.forwardFromMessageId())}` for force post likes update"
                    )
                )
            }
        } ?: if (commandRegex.matches(message.text()) && adminsHolder.contains(userId)) {
            val messageId = message.text().split(" ")[1].toInt()

            if (messageId in likesPluginRegisteredLikesMessagesTable) {
                updateChannel.send(messageId)

                botWR.get() ?.executeAsync(
                    SendMessage(
                        message.chat().id(),
                        "Likes was updated (can be showed with delay)"
                    )
                )
            }
        }
    }
}
