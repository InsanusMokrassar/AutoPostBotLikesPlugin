package com.github.insanusmokrassar.AutoPostBotLikesPlugin.listeners

import com.github.insanusmokrassar.AutoPostBotLikesPlugin.database.LikesPluginLikesTable
import com.github.insanusmokrassar.AutoPostTelegramBot.realCallbackQueryListener
import com.github.insanusmokrassar.AutoPostTelegramBot.utils.extensions.*
import com.pengrad.telegrambot.TelegramBot
import com.pengrad.telegrambot.model.request.InlineKeyboardButton
import java.lang.ref.WeakReference

private const val dislikeCallbackData = "like_plugin dislike"
fun createDislikeButton(text: String): InlineKeyboardButton = InlineKeyboardButton(text).also {
    it.callbackData(dislikeCallbackData)
}

class DislikesListener(
    targetChatId: Long,
    likesPluginLikesTable: LikesPluginLikesTable,
    answerText: String,
    botWR: WeakReference<TelegramBot>
) {
    init {
        realCallbackQueryListener.broadcastChannel.subscribeChecking {
            val bot = botWR.get() ?: return@subscribeChecking false
            val chatId = it.second.message() ?.chat() ?.id() ?: return@subscribeChecking true
            val data = it.second.data()
            if (chatId == targetChatId && data == dislikeCallbackData) {
                val messageId = it.second.message().messageId()
                val userId = it.second.from().id()

                likesPluginLikesTable.userDislikePost(userId.toLong(), messageId)

                bot.queryAnswer(
                    it.second.id(),
                    answerText
                )
            }
            true
        }
    }
}