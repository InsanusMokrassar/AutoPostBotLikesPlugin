package com.github.insanusmokrassar.AutoPostBotLikesPlugin.listeners

import com.github.insanusmokrassar.AutoPostBotLikesPlugin.LikePluginConfig
import com.github.insanusmokrassar.AutoPostBotLikesPlugin.database.LikesPluginLikesTable
import com.github.insanusmokrassar.AutoPostBotLikesPlugin.database.LikesPluginRegisteredLikesMessagesTable
import com.github.insanusmokrassar.AutoPostTelegramBot.utils.extensions.*
import com.pengrad.telegrambot.TelegramBot
import com.pengrad.telegrambot.model.request.InlineKeyboardButton
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
        likesPluginLikesTable.likesChannel.subscribeChecking {
            val bot = botWR.get() ?: return@subscribeChecking false

            updateMessage(bot, it.first)

            true
        }
        likesPluginLikesTable.dislikesChannel.subscribeChecking {
            val bot = botWR.get() ?: return@subscribeChecking false

            updateMessage(bot, it.first)

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
            )
        )
    }

    private fun createMarkup(messageId: Int): InlineKeyboardMarkup {
        return InlineKeyboardMarkup(
            listOfNotNull(
                createLikeButton(
                    likesPluginLikesTable.postLikes(messageId)
                ),
                createDislikeButton(
                    likesPluginLikesTable.postDislikes(messageId)
                )
            ).toTypedArray()
        )
    }

    private fun createLikeButton(count: Int): InlineKeyboardButton {
        return createLikeButton(
            "${likePluginConfig.likeText} $count"
        )
    }

    private fun createDislikeButton(count: Int): InlineKeyboardButton? {
        return likePluginConfig.dislikeText ?.let {
            createDislikeButton(
                "$it $count"
            )
        }
    }
}