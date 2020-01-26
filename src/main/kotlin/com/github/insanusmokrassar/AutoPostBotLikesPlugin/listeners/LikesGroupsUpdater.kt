package com.github.insanusmokrassar.AutoPostBotLikesPlugin.listeners

import com.github.insanusmokrassar.AutoPostBotLikesPlugin.database.LikesPluginLikesTable
import com.github.insanusmokrassar.AutoPostBotLikesPlugin.database.LikesPluginRegisteredLikesMessagesTable
import com.github.insanusmokrassar.AutoPostBotLikesPlugin.models.ButtonMark
import com.github.insanusmokrassar.AutoPostBotLikesPlugin.models.config.Group
import com.github.insanusmokrassar.AutoPostTelegramBot.base.plugins.commonLogger
import com.github.insanusmokrassar.AutoPostTelegramBot.utils.NewDefaultCoroutineScope
import com.github.insanusmokrassar.AutoPostTelegramBot.utils.flow.collectWithErrors
import com.github.insanusmokrassar.TelegramBotAPI.bot.RequestsExecutor
import com.github.insanusmokrassar.TelegramBotAPI.requests.edit.ReplyMarkup.EditChatMessageReplyMarkup
import com.github.insanusmokrassar.TelegramBotAPI.types.ChatId
import com.github.insanusmokrassar.TelegramBotAPI.types.MessageIdentifier
import com.github.insanusmokrassar.TelegramBotAPI.types.buttons.InlineKeyboardMarkup
import com.github.insanusmokrassar.TelegramBotAPI.utils.extensions.executeUnsafe
import com.github.insanusmokrassar.TelegramBotAPI.utils.matrix
import com.github.insanusmokrassar.TelegramBotAPI.utils.row
import com.soywiz.klock.DateTime
import com.soywiz.klock.TimeSpan
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.asFlow
import java.lang.ref.WeakReference

private sealed class UpdateCommand()

private class PendingUpdate(val messageId: MessageIdentifier)
private class CallUpdate(val messageId: MessageIdentifier)

class LikesGroupsUpdater(
    private val likesPluginLikesTable: LikesPluginLikesTable,
    likesPluginRegisteredLikesMessagesTable: LikesPluginRegisteredLikesMessagesTable,
    private val botWR: WeakReference<RequestsExecutor>,
    private val chatId: ChatId,
    private val debounceDelay: Long,
    private val adaptedGroups: List<Group>,
    scope: CoroutineScope
) {
    private val pendingUpdatesQueue: Channel<MessageIdentifier> = Channel()
    private val updateCaller = scope.launch {
        val updateCalls = mutableMapOf<MessageIdentifier, Job>()
        val times = mutableMapOf<MessageIdentifier, Long>()
        for (messageIdentifier in pendingUpdatesQueue) {
            times[messageIdentifier] = System.currentTimeMillis() + debounceDelay
            updateCalls.getOrPut(messageIdentifier) {
                launch {
                    try {
                        var timeToSleep = times[messageIdentifier] ?.minus(System.currentTimeMillis()) ?: 0
                        while (timeToSleep > 0) {
                            delay(timeToSleep)
                            timeToSleep = times[messageIdentifier] ?.minus(System.currentTimeMillis()) ?: 0
                        }
                        updateCalls[messageIdentifier] = launch {
                            updateMessage(messageIdentifier)
                            updateCalls.remove(messageIdentifier)
                        }
                        times.remove(messageIdentifier)
                    } catch (e: Exception) {
                        commonLogger.throwing(this@LikesGroupsUpdater::class.simpleName, "call update", e)
                    }
                }
            }
        }
    }

    init {
        scope.apply {
            launch {
                likesPluginLikesTable.messageButtonsUpdatedChannel.asFlow().collectWithErrors {
                    pendingUpdate(it)
                }
            }
            launch {
                likesPluginRegisteredLikesMessagesTable.messageIdAllocatedChannel.asFlow().collectWithErrors {
                    pendingUpdate(it)
                }
            }
        }
    }

    private suspend fun pendingUpdate(messageId: MessageIdentifier) {
        pendingUpdatesQueue.send(messageId)
    }

    private suspend fun updateMessage(messageId: MessageIdentifier) {
        botWR.get() ?.executeUnsafe(
            EditChatMessageReplyMarkup(
                chatId,
                messageId,
                replyMarkup = createMarkup(messageId)
            )
        )
    }

    private fun createMarkup(messageId: MessageIdentifier): InlineKeyboardMarkup {
        val buttonMarks = likesPluginLikesTable.getMessageButtonMarks(messageId).map {
            it.buttonId to it
        }.toMap()
        return InlineKeyboardMarkup(
            matrix {
                adaptedGroups.forEach { group ->
                    row {
                        group.items.forEach { button ->
                            val mark = buttonMarks[button.id] ?: ButtonMark(
                                messageId,
                                button.id,
                                0
                            )
                            add(
                                createMarkButton(
                                    button,
                                    mark
                                )
                            )
                        }
                    }
                }
            }
        )
    }
}