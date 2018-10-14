package com.github.insanusmokrassar.AutoPostBotLikesPlugin.listeners

import com.github.insanusmokrassar.AutoPostBotLikesPlugin.database.LikesPluginLikesTable
import com.github.insanusmokrassar.AutoPostBotLikesPlugin.database.LikesPluginRegisteredLikesMessagesTable
import com.github.insanusmokrassar.AutoPostBotLikesPlugin.models.ButtonMark
import com.github.insanusmokrassar.AutoPostBotLikesPlugin.models.config.LikePluginConfig
import com.github.insanusmokrassar.AutoPostBotLikesPlugin.utils.extensions.debounceByValue
import com.github.insanusmokrassar.AutoPostTelegramBot.base.plugins.commonLogger
import com.github.insanusmokrassar.AutoPostTelegramBot.utils.extensions.*
import com.pengrad.telegrambot.TelegramBot
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup
import com.pengrad.telegrambot.request.EditMessageReplyMarkup
import kotlinx.coroutines.experimental.CancellationException
import kotlinx.coroutines.experimental.channels.*
import java.lang.ref.WeakReference

class RatingChangedListener(
    private val likesPluginLikesTable: LikesPluginLikesTable,
    private val likesPluginRegisteredLikesMessagesTable: LikesPluginRegisteredLikesMessagesTable,
    private val botWR: WeakReference<TelegramBot>,
    private val chatId: Long,
    private val likePluginConfig: LikePluginConfig
) {
    private val debounceDelay: Long = likePluginConfig.debounceDelay
    private val retriesDelay = debounceDelay / 2

    init {
        likesPluginLikesTable.messageButtonsUpdatedChannel.debounceByValue(debounceDelay).subscribe {
            updateMessage(it)
        }
        likesPluginRegisteredLikesMessagesTable.messageIdAllocatedChannel.subscribe {
            updateMessage(it)
        }
    }

    private fun updateMessage(messageId: Int) {
        botWR.get() ?.executeAsync(
            EditMessageReplyMarkup(
                chatId,
                messageId
            ).replyMarkup(
                createMarkup(messageId)
            ),
            onFailure = {
                _, ioException ->
                commonLogger.warning("Can't edit message $messageId for applying: ${ioException ?.message ?: "unknown problem"}")
            },
            retries = 3,
            retriesDelay = retriesDelay / 2
        )
    }

    private fun createMarkup(messageId: Int): InlineKeyboardMarkup {
        val buttonMarks = likesPluginLikesTable.getMessageButtonMarks(messageId).map {
            it.buttonId to it
        }.toMap()
        return InlineKeyboardMarkup(
            *likePluginConfig.adaptedGroups.map {
                group ->
                group.items.map {
                    button ->
                    val mark = buttonMarks[button.id] ?: ButtonMark(
                        messageId,
                        button.id,
                        0
                    )
                    createMarkButton(
                        button,
                        mark
                    )
                }.toTypedArray()
            }.toTypedArray()
        )
    }
}