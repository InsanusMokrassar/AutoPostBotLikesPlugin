package com.github.insanusmokrassar.AutoPostBotLikesPlugin.listeners

import com.github.insanusmokrassar.AutoPostBotLikesPlugin.database.LikesPluginLikesTable
import com.github.insanusmokrassar.AutoPostBotLikesPlugin.models.ButtonMark
import com.github.insanusmokrassar.AutoPostBotLikesPlugin.models.Mark
import com.github.insanusmokrassar.AutoPostBotLikesPlugin.models.config.ButtonConfig
import com.github.insanusmokrassar.AutoPostTelegramBot.base.plugins.commonLogger
import com.github.insanusmokrassar.AutoPostTelegramBot.flowFilter
import com.github.insanusmokrassar.AutoPostTelegramBot.utils.flow.collectWithErrors
import com.github.insanusmokrassar.TelegramBotAPI.bot.RequestsExecutor
import com.github.insanusmokrassar.TelegramBotAPI.requests.answers.createAnswer
import com.github.insanusmokrassar.TelegramBotAPI.types.*
import com.github.insanusmokrassar.TelegramBotAPI.types.CallbackQuery.MessageDataCallbackQuery
import com.github.insanusmokrassar.TelegramBotAPI.types.buttons.InlineKeyboardButtons.CallbackDataInlineKeyboardButton
import com.github.insanusmokrassar.TelegramBotAPI.types.buttons.InlineKeyboardButtons.InlineKeyboardButton
import com.github.insanusmokrassar.TelegramBotAPI.utils.extensions.executeUnsafe
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.flow.*


private const val like_plugin_data = "like_plugin "
private const val markCallbackData = "$like_plugin_data%s"
fun createMarkButton(buttonConfig: ButtonConfig, buttonMark: ButtonMark): InlineKeyboardButton = CallbackDataInlineKeyboardButton(
    buttonConfig.format(buttonMark.count),
    markCallbackData.format(buttonConfig.id)
)

private fun CoroutineScope.createActor(
    updateFlow: Flow<MessageDataCallbackQuery>,
    targetChatId: ChatId,
    likesPluginLikesTable: LikesPluginLikesTable,
    radioButtonsIds: List<String>,
    bot: RequestsExecutor,
    button: ButtonConfig
) {
    val buttonId = button.id
    val answeringExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        commonLogger.throwing("Mark ${button.text}", "sending answer", throwable)
    }
    launch {
        updateFlow.collectWithErrors { query ->
            val chatId = query.message.chat.id
            val data = query.data
            if (chatId == targetChatId && data.startsWith(like_plugin_data) && data.split(" ")[1] == buttonId) {
                val messageId = query.message.messageId
                val userId = query.user.id

                val mark = Mark(userId.chatId, messageId, buttonId)

                val marked = likesPluginLikesTable.insertMarkDeleteOther(
                    mark,
                    radioButtonsIds
                )

                supervisorScope {
                    launch(answeringExceptionHandler) {
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
}

private fun CoroutineScope.createActor(
    updateFlow: Flow<MessageDataCallbackQuery>,
    targetChatId: ChatId,
    likesPluginLikesTable: LikesPluginLikesTable,
    bot: RequestsExecutor,
    button: ButtonConfig
) {
    val buttonId = button.id
    launch {
        updateFlow.collectWithErrors { query ->
            val chatId = query.message.chat.id
            val data = query.data
            if (chatId == targetChatId && data.startsWith(like_plugin_data) && data.split(" ")[1] == buttonId) {
                val messageId = query.message.messageId
                val userId = query.user.id

                val mark = Mark(userId.chatId, messageId, buttonId)

                val marked = likesPluginLikesTable.insertOrDeleteMark(mark)

                bot.executeUnsafe(
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

private suspend fun createActor(
    updateFlow: Flow<MessageDataCallbackQuery>,
    targetChatId: ChatId,
    likesPluginLikesTable: LikesPluginLikesTable,
    bot: RequestsExecutor,
    button: ButtonConfig,
    radioButtons: List<String>? = null
) {
    supervisorScope {
        radioButtons ?.let {
            createActor(updateFlow, targetChatId, likesPluginLikesTable, radioButtons, bot, button)
        } ?: createActor(updateFlow, targetChatId, likesPluginLikesTable, bot, button)
    }
}

fun CoroutineScope.enableMarksListener(
    targetChatId: ChatId,
    likesPluginLikesTable: LikesPluginLikesTable,
    buttons: List<ButtonConfig>,
    radioGroupsByOneOfButtons: Map<ButtonConfig, List<String>>,
    bot: RequestsExecutor
) {
    val associatedButtons = buttons.associateBy { it.id }
    val internalChannel = Channel<MessageDataCallbackQuery>(Channel.UNLIMITED)
    val asFlow = internalChannel.consumeAsFlow()

    launch {
        asFlow.collectWithErrors { query ->
            async {
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
            }.invokeOnCompletion {
                it ?.let { e ->
                    commonLogger.throwing(
                        "MarksListener",
                        "Mark update handling",
                        e
                    )
                }
            }
        }
    }
    launch {
        flowFilter.callbackQueryFlow.collectWithErrors {
            internalChannel.send(it.data as? MessageDataCallbackQuery ?: return@collectWithErrors)
        }
    }
}
