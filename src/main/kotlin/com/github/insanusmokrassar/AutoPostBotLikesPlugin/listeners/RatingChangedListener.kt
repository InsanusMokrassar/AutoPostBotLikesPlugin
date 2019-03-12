package com.github.insanusmokrassar.AutoPostBotLikesPlugin.listeners

import com.github.insanusmokrassar.AutoPostBotLikesPlugin.database.LikesPluginLikesTable
import com.github.insanusmokrassar.AutoPostBotLikesPlugin.database.LikesPluginRegisteredLikesMessagesTable
import com.github.insanusmokrassar.AutoPostBotLikesPlugin.models.ButtonMark
import com.github.insanusmokrassar.AutoPostBotLikesPlugin.models.config.Group
import com.github.insanusmokrassar.AutoPostTelegramBot.utils.extensions.subscribe
import com.github.insanusmokrassar.TelegramBotAPI.bot.RequestsExecutor
import com.github.insanusmokrassar.TelegramBotAPI.requests.edit.ReplyMarkup.EditChatMessageReplyMarkup
import com.github.insanusmokrassar.TelegramBotAPI.types.ChatId
import com.github.insanusmokrassar.TelegramBotAPI.types.MessageIdentifier
import com.github.insanusmokrassar.TelegramBotAPI.types.buttons.InlineKeyboardMarkup
import com.github.insanusmokrassar.TelegramBotAPI.utils.extensions.debounceByValue
import com.github.insanusmokrassar.TelegramBotAPI.utils.matrix
import com.github.insanusmokrassar.TelegramBotAPI.utils.row
import com.github.insanusmokrassar.TelegramBotAPI.utils.extensions.executeUnsafe
import java.lang.ref.WeakReference

class RatingChangedListener(
    private val likesPluginLikesTable: LikesPluginLikesTable,
    likesPluginRegisteredLikesMessagesTable: LikesPluginRegisteredLikesMessagesTable,
    private val botWR: WeakReference<RequestsExecutor>,
    private val chatId: ChatId,
    private val debounceDelay: Long,
    private val adaptedGroups: List<Group>
) {
    init {
        likesPluginLikesTable.messageButtonsUpdatedChannel.openSubscription().debounceByValue(debounceDelay).subscribe {
            updateMessage(it)
        }
        likesPluginRegisteredLikesMessagesTable.messageIdAllocatedChannel.openSubscription().subscribe {
            updateMessage(it)
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