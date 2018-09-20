package com.github.insanusmokrassar.AutoPostBotLikesPlugin.listeners

import com.github.insanusmokrassar.AutoPostBotLikesPlugin.database.LikesPluginLikesTable
import com.github.insanusmokrassar.AutoPostBotLikesPlugin.models.ButtonMark
import com.github.insanusmokrassar.AutoPostBotLikesPlugin.models.Mark
import com.github.insanusmokrassar.AutoPostBotLikesPlugin.models.config.ButtonConfig
import com.github.insanusmokrassar.AutoPostTelegramBot.realCallbackQueryListener
import com.github.insanusmokrassar.AutoPostTelegramBot.utils.extensions.queryAnswer
import com.github.insanusmokrassar.AutoPostTelegramBot.utils.extensions.subscribeChecking
import com.pengrad.telegrambot.TelegramBot
import com.pengrad.telegrambot.model.request.InlineKeyboardButton
import java.lang.ref.WeakReference


private const val like_plugin_data = "like_plugin "
private const val markCallbackData = "$like_plugin_data%s"
fun createMarkButton(buttonConfig: ButtonConfig, buttonMark: ButtonMark): InlineKeyboardButton = InlineKeyboardButton(
    buttonConfig.format(buttonMark.count)
).also {
    it.callbackData(markCallbackData.format(buttonConfig.id))
}

class MarkListener(
    targetChatId: Long,
    likesPluginLikesTable: LikesPluginLikesTable,
    button: ButtonConfig,
    botWR: WeakReference<TelegramBot>,
    private val radioGroupIds: List<String>? = null
) {
    private val buttonId: String = button.id

    init {
        realCallbackQueryListener.broadcastChannel.subscribeChecking {
            val bot = botWR.get() ?: return@subscribeChecking false
            val chatId = it.second.message() ?.chat() ?.id() ?: return@subscribeChecking true
            val data = it.second.data()
            if (chatId == targetChatId && data.startsWith(like_plugin_data) && data.split(" ")[1] == buttonId) {
                val messageId = it.second.message().messageId()
                val userId = it.second.from().id()

                val mark = Mark(userId.toLong(), messageId, buttonId)

                val marked = radioGroupIds ?.let {
                    radioButtonsIds ->
                    likesPluginLikesTable.insertMarkDeleteOther(
                        mark,
                        radioButtonsIds
                    )
                } ?: likesPluginLikesTable.insertOrDeleteMark(mark)

                bot.queryAnswer(
                    it.second.id(),
                    if (marked) {
                        button.positiveAnswer ?.text ?: ""
                    } else {
                        button.negativeAnswer ?.text ?: ""
                    }
                )
            }
            true
        }
    }
}
