package com.github.insanusmokrassar.AutoPostBotLikesPlugin.listeners

import com.github.insanusmokrassar.AutoPostBotLikesPlugin.database.LikesPluginLikesTable
import com.github.insanusmokrassar.AutoPostBotLikesPlugin.models.ButtonMark
import com.github.insanusmokrassar.AutoPostBotLikesPlugin.models.Mark
import com.github.insanusmokrassar.AutoPostBotLikesPlugin.models.config.ButtonConfig
import com.github.insanusmokrassar.AutoPostTelegramBot.base.plugins.commonLogger
import com.github.insanusmokrassar.AutoPostTelegramBot.flowFilter
import com.github.insanusmokrassar.AutoPostTelegramBot.utils.flow.collectWithErrors
import com.github.insanusmokrassar.TelegramBotAPI.bot.RequestsExecutor
import com.github.insanusmokrassar.TelegramBotAPI.bot.exceptions.RequestException
import com.github.insanusmokrassar.TelegramBotAPI.requests.answers.createAnswer
import com.github.insanusmokrassar.TelegramBotAPI.types.*
import com.github.insanusmokrassar.TelegramBotAPI.types.CallbackQuery.MessageDataCallbackQuery
import com.github.insanusmokrassar.TelegramBotAPI.types.buttons.InlineKeyboardButtons.CallbackDataInlineKeyboardButton
import com.github.insanusmokrassar.TelegramBotAPI.types.buttons.InlineKeyboardButtons.InlineKeyboardButton
import com.github.insanusmokrassar.TelegramBotAPI.utils.extensions.executeUnsafe
import kotlinx.coroutines.*
import java.lang.Exception
import java.lang.ref.WeakReference
import java.util.concurrent.Executors


private const val like_plugin_data = "like_plugin "
private const val markCallbackData = "$like_plugin_data%s"
fun createMarkButton(buttonConfig: ButtonConfig, buttonMark: ButtonMark): InlineKeyboardButton = CallbackDataInlineKeyboardButton(
    buttonConfig.format(buttonMark.count),
    markCallbackData.format(buttonConfig.id)
)

fun enableMarksListener(
    targetChatId: ChatId,
    likesPluginLikesTable: LikesPluginLikesTable,
    buttons: List<ButtonConfig>,
    radioGroupsByOneOfButtons: Map<ButtonConfig, List<String>>,
    bot: RequestsExecutor,
    jobsCount: Int = 64
) {
    val associatedButtons = buttons.associateBy { it.id }
    CoroutineScope(Executors.newFixedThreadPool(jobsCount).asCoroutineDispatcher()).launch {
        flowFilter.callbackQueryFlow.collectWithErrors {
            val query = (it.data as? MessageDataCallbackQuery) ?: return@collectWithErrors
            launch {
                try {
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
                } catch (e: Exception) {
                    commonLogger.throwing(
                        "MarksListener",
                        "Mark update handling",
                        e
                    )
                }
            }
        }
    }
}
