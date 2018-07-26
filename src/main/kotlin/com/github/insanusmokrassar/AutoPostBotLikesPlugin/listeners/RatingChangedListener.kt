package com.github.insanusmokrassar.AutoPostBotLikesPlugin.listeners

import com.github.insanusmokrassar.AutoPostBotLikesPlugin.LikePluginConfig
import com.github.insanusmokrassar.AutoPostBotLikesPlugin.database.LikesPluginLikesTable
import com.github.insanusmokrassar.AutoPostBotLikesPlugin.database.LikesPluginRegisteredLikesMessagesTable
import com.github.insanusmokrassar.AutoPostTelegramBot.utils.extensions.*
import com.pengrad.telegrambot.TelegramBot
import com.pengrad.telegrambot.model.request.InlineKeyboardButton
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup
import com.pengrad.telegrambot.request.EditMessageReplyMarkup
import kotlinx.coroutines.experimental.*
import java.lang.ref.WeakReference
import java.util.*

class RatingChangedListener(
    private val likesPluginLikesTable: LikesPluginLikesTable,
    private val likesPluginRegisteredLikesMessagesTable: LikesPluginRegisteredLikesMessagesTable,
    private val botWR: WeakReference<TelegramBot>,
    private val chatId: Long,
    private val likePluginConfig: LikePluginConfig
) {
    private val updateQueue = ArrayDeque<Int>()
    private var updateJob: Job? = null

    init {
        likesPluginLikesTable.likesChannel.subscribe {
            updateJob(it.first)
        }
        likesPluginLikesTable.dislikesChannel.subscribe {
            updateJob(it.first)
        }
        likesPluginRegisteredLikesMessagesTable.messageIdAllocatedChannel.subscribe {
            updateJob(it)
        }
    }

    private fun updateJob(messageId: Int) {
        if (updateQueue.contains(messageId)) {
            updateQueue.remove(messageId)
        }
        updateQueue.offer(messageId)

        updateJob ?:let {
            updateJob = launch {
                while (isActive && updateQueue.isNotEmpty()) {
                    updateMessage(
                        botWR.get() ?: break,
                        updateQueue.pop()
                    )

                    delay(likePluginConfig.updatesDelay)
                }
                updateJob = null
            }
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