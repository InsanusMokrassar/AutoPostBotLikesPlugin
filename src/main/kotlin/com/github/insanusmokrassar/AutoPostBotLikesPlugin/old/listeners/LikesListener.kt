package com.github.insanusmokrassar.AutoPostBotLikesPlugin.old.listeners

import com.github.insanusmokrassar.AutoPostBotLikesPlugin.old.database.LikesPluginLikesTable
import com.github.insanusmokrassar.AutoPostTelegramBot.realCallbackQueryListener
import com.github.insanusmokrassar.AutoPostTelegramBot.utils.extensions.*
import com.pengrad.telegrambot.TelegramBot
import com.pengrad.telegrambot.model.request.InlineKeyboardButton
import java.lang.ref.WeakReference

private const val likeCallbackData = "like_plugin %s"
fun createLikeButton(identifier: String): InlineKeyboardButton = InlineKeyboardButton(identifier).also {
    it.callbackData(likeCallbackData.format(identifier))
}

class LikesListener(
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
            if (chatId == targetChatId && data == likeCallbackData) {
                val messageId = it.second.message().messageId()
                val userId = it.second.from().id()

                likesPluginLikesTable.userLikePost(userId.toLong(), messageId)

                bot.queryAnswer(
                    it.second.id(),
                    answerText
                )
            }
            true
        }
    }
}