package com.github.insanusmokrassar.AutoPostBotLikesPlugin.listeners

import com.github.insanusmokrassar.AutoPostBotLikesPlugin.database.LikesPluginLikesTable
import com.github.insanusmokrassar.AutoPostBotLikesPlugin.database.LikesPluginRegisteredLikesMessagesTable
import com.github.insanusmokrassar.AutoPostBotLikesPlugin.models.ButtonMark
import com.github.insanusmokrassar.AutoPostBotLikesPlugin.models.config.Group
import com.github.insanusmokrassar.AutoPostTelegramBot.base.plugins.commonLogger
import com.github.insanusmokrassar.AutoPostTelegramBot.utils.NewDefaultCoroutineScope
import com.github.insanusmokrassar.AutoPostTelegramBot.utils.extensions.subscribe
import com.github.insanusmokrassar.TelegramBotAPI.bot.RequestsExecutor
import com.github.insanusmokrassar.TelegramBotAPI.requests.edit.ReplyMarkup.EditChatMessageReplyMarkup
import com.github.insanusmokrassar.TelegramBotAPI.types.ChatId
import com.github.insanusmokrassar.TelegramBotAPI.types.MessageIdentifier
import com.github.insanusmokrassar.TelegramBotAPI.types.buttons.InlineKeyboardMarkup
import com.github.insanusmokrassar.TelegramBotAPI.utils.matrix
import com.github.insanusmokrassar.TelegramBotAPI.utils.row
import com.github.insanusmokrassar.TelegramBotAPI.utils.extensions.executeUnsafe
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class LikesGroupsUpdater(
    private val likesPluginLikesTable: LikesPluginLikesTable,
    likesPluginRegisteredLikesMessagesTable: LikesPluginRegisteredLikesMessagesTable,
    private val botWR: WeakReference<RequestsExecutor>,
    private val chatId: ChatId,
    private val debounceDelay: Long,
    private val adaptedGroups: List<Group>
) {
    private val scope = NewDefaultCoroutineScope(4)
    private val pendingUpdatesChannel: Channel<MessageIdentifier> = Channel(Channel.UNLIMITED)
    private val pendingUpdatesQueue: MutableSet<MessageIdentifier> = Collections.newSetFromMap(ConcurrentHashMap())
    private val updateCaller = scope.launch {
        for (messageIdentifier in pendingUpdatesChannel) {
            pendingUpdatesQueue.remove(messageIdentifier)
            try {
                updateMessage(messageIdentifier)
            } catch (e: Exception) {
                commonLogger.throwing(this@LikesGroupsUpdater::class.simpleName, "call update", e)
            }
            delay(debounceDelay)
        }
    }

    init {
        likesPluginLikesTable.messageButtonsUpdatedChannel.openSubscription().subscribe(scope = scope) {
            pendingUpdate(it)
        }
        likesPluginRegisteredLikesMessagesTable.messageIdAllocatedChannel.openSubscription().subscribe(scope = scope) {
            pendingUpdate(it)
        }
    }

    private suspend fun pendingUpdate(messageId: MessageIdentifier) {
        if (pendingUpdatesQueue.add(messageId)) {
            pendingUpdatesChannel.send(messageId)
        }
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