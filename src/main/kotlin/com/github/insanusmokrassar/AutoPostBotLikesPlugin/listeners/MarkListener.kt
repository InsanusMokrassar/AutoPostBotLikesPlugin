package com.github.insanusmokrassar.AutoPostBotLikesPlugin.listeners

import com.github.insanusmokrassar.AutoPostBotLikesPlugin.database.LikesPluginLikesTable
import com.github.insanusmokrassar.AutoPostBotLikesPlugin.models.ButtonMark
import com.github.insanusmokrassar.AutoPostBotLikesPlugin.models.Mark
import com.github.insanusmokrassar.AutoPostBotLikesPlugin.models.config.ButtonConfig
import com.github.insanusmokrassar.AutoPostTelegramBot.flowFilter
import com.github.insanusmokrassar.AutoPostTelegramBot.utils.flow.collectWithErrors
import com.github.insanusmokrassar.TelegramBotAPI.bot.RequestsExecutor
import com.github.insanusmokrassar.TelegramBotAPI.requests.answers.createAnswer
import com.github.insanusmokrassar.TelegramBotAPI.types.CallbackQuery.MessageDataCallbackQuery
import com.github.insanusmokrassar.TelegramBotAPI.types.ChatId
import com.github.insanusmokrassar.TelegramBotAPI.types.buttons.InlineKeyboardButtons.CallbackDataInlineKeyboardButton
import com.github.insanusmokrassar.TelegramBotAPI.types.buttons.InlineKeyboardButtons.InlineKeyboardButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference


private const val like_plugin_data = "like_plugin "
private const val markCallbackData = "$like_plugin_data%s"
fun createMarkButton(buttonConfig: ButtonConfig, buttonMark: ButtonMark): InlineKeyboardButton = CallbackDataInlineKeyboardButton(
    buttonConfig.format(buttonMark.count),
    markCallbackData.format(buttonConfig.id)
)

fun CoroutineScope.enableMarksListener(
    targetChatId: ChatId,
    likesPluginLikesTable: LikesPluginLikesTable,
    buttons: List<ButtonConfig>,
    radioGroupsByOneOfButtons: Map<ButtonConfig, List<String>>,
    botWR: WeakReference<RequestsExecutor>
) {
    val associatedButtons = buttons.associateBy { it.id }
    launch {
        flowFilter.callbackQueryFlow.collectWithErrors {
            val query = it.data as? MessageDataCallbackQuery ?: return@collectWithErrors
            val bot = botWR.get() ?: return@collectWithErrors
            val chatId = query.message.chat.id
            val data = query.data
            if (chatId == targetChatId && data.startsWith(like_plugin_data)) {
                val buttonId = data.split(" ")[1]
                associatedButtons[buttonId] ?.let { button ->
                    val messageId = query.message.messageId
                    val userId = query.user.id

                    val mark = Mark(userId.chatId, messageId, buttonId)

                    val marked = radioGroupsByOneOfButtons[button] ?.let { radioButtonsIds ->
                        likesPluginLikesTable.insertMarkDeleteOther(
                            mark,
                            radioButtonsIds
                        )
                    } ?: likesPluginLikesTable.insertOrDeleteMark(mark)

                    bot.execute(
                        query.createAnswer(
                            if (marked) {
                                button.positiveAnswer ?.text ?: ""
                            } else {
                                button.negativeAnswer ?.text ?: ""
                            }
                        )
                    )
                }
            }
        }
    }
}
