package com.github.insanusmokrassar.AutoPostBotLikesPlugin.listeners

import com.github.insanusmokrassar.AutoPostBotLikesPlugin.database.LikesPluginLikesTable
import com.github.insanusmokrassar.AutoPostBotLikesPlugin.database.LikesPluginRegisteredLikesMessagesTable
import com.github.insanusmokrassar.AutoPostBotLikesPlugin.models.config.LikePluginConfig
import com.github.insanusmokrassar.AutoPostTelegramBot.utils.extensions.executeAsync
import com.github.insanusmokrassar.AutoPostTelegramBot.utils.extensions.subscribeChecking
import com.pengrad.telegrambot.TelegramBot
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup
import com.pengrad.telegrambot.request.EditMessageReplyMarkup
import java.lang.ref.WeakReference

class RatingChangedListener(
    private val likesPluginLikesTable: LikesPluginLikesTable,
    private val likesPluginRegisteredLikesMessagesTable: LikesPluginRegisteredLikesMessagesTable,
    botWR: WeakReference<TelegramBot>,
    private val chatId: Long,
    private val likePluginConfig: LikePluginConfig
) {
    init {
        likesPluginLikesTable.messageButtonsUpdatedChannel.subscribeChecking {
            val bot = botWR.get() ?: return@subscribeChecking false

            updateMessage(bot, it)

            true
        }
        likesPluginRegisteredLikesMessagesTable.messageIdAllocatedChannel.subscribeChecking {
            val bot = botWR.get() ?: return@subscribeChecking false

            updateMessage(bot, it)

            true
        }
    }

    private fun updateMessage(
        bot: TelegramBot,
        messageId: Int
    ) {
        bot.executeAsync(
            EditMessageReplyMarkup(
                chatId,
                messageId
            ).replyMarkup(
                createMarkup(messageId)
            ),
            retries = 3
        )
    }

    private fun createMarkup(messageId: Int): InlineKeyboardMarkup {
        val buttonMarks = likesPluginLikesTable.getMessageButtonMarks(messageId)
        return InlineKeyboardMarkup(
            *likePluginConfig.adaptedGroups.map {
                group ->
                group.items.mapNotNull {
                    button ->
                    val mark = buttonMarks.firstOrNull { it.buttonId == button.id }
                    mark ?.let {
                        createMarkButton(
                            button,
                            mark
                        )
                    }
                }.toTypedArray()
            }.toTypedArray()
        )
    }
}