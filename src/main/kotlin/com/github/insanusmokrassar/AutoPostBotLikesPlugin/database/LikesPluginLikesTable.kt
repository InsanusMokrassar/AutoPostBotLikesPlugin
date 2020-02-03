package com.github.insanusmokrassar.AutoPostBotLikesPlugin.database

import com.github.insanusmokrassar.AutoPostBotLikesPlugin.models.ButtonMark
import com.github.insanusmokrassar.AutoPostBotLikesPlugin.models.Mark
import com.github.insanusmokrassar.AutoPostTelegramBot.utils.NewDefaultCoroutineScope
import com.github.insanusmokrassar.AutoPostTelegramBot.utils.extensions.subscribe
import com.github.insanusmokrassar.TelegramBotAPI.types.MessageIdentifier
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime

private val LikesPluginLikesTableScope = NewDefaultCoroutineScope(1)

class LikesPluginLikesTable(
    likesPluginRegisteredLikesMessagesTable: LikesPluginRegisteredLikesMessagesTable,
    private val database: Database
) : Table() {
    val messageButtonsUpdatedChannel = BroadcastChannel<MessageIdentifier>(Channel.CONFLATED)

    private val idColumn = integer("id").primaryKey().autoIncrement()
    private val userIdColumn = long("userId")
    private val messageIdColumn = long("messageId")
    private val buttonIdColumn = text("buttonId")
    private val dateTimeColumn = datetime("markDateTime").default(DateTime.now())

    private val ResultRow.buttonId: String
        get() = get(buttonIdColumn)

    private val ResultRow.messageId: MessageIdentifier
        get() = get(messageIdColumn)

    private val ResultRow.userId: Long
        get() = get(userIdColumn)

    init {
        transaction(database) {
            SchemaUtils.createMissingTablesAndColumns(this@LikesPluginLikesTable)
        }
        likesPluginRegisteredLikesMessagesTable.messageIdRemovedChannel.subscribe {
            transaction(database) {
                deleteWhere {
                    messageIdColumn.eq(it)
                }
            }
        }
    }

    operator fun contains(mark: Mark): Boolean {
        return transaction(database) {
            select {
                makeSelectStatement(mark)
            }.count() > 0
        }
    }

    private fun makeSelectStatement(mark: Mark): Op<Boolean> {
        return userIdColumn.eq(
            mark.userId
        ).and(
            messageIdColumn.eq(
                mark.messageId
            )
        ).and(
            buttonIdColumn.eq(
                mark.buttonId
            )
        )
    }

    private fun insertMark(mark: Mark): Boolean {
        return transaction(database) {
            if (contains(mark)) {
                false
            } else {
                insert {
                    it[userIdColumn] = mark.userId
                    it[messageIdColumn] = mark.messageId
                    it[buttonIdColumn] = mark.buttonId
                    it[dateTimeColumn] = DateTime.now()
                }[idColumn] != null
            }
        }.also {
            if (it) {
                LikesPluginLikesTableScope.launch {
                    messageButtonsUpdatedChannel.send(mark.messageId)
                }
            }
        }
    }

    private fun deleteMark(mark: Mark): Boolean {
        return transaction(database) {
            deleteWhere {
                makeSelectStatement(mark)
            } > 0
        }.also {
            if (it) {
                LikesPluginLikesTableScope.launch {
                    messageButtonsUpdatedChannel.send(mark.messageId)
                }
            }
        }
    }

    private fun deleteUserMarksOnMessage(messageId: MessageIdentifier, userId: Long, buttonIds: List<String>?): Int {
        return transaction(database) {
            userIdColumn.eq(
                userId
            ).and(
                messageIdColumn.eq(
                    messageId
                )
            ).let {
                buttonIds ?.let {
                    buttonIds ->
                    it.and(
                        buttonIdColumn.inList(buttonIds)
                    )
                }
            } ?.let {
                deleteWhere {
                    it
                }
            } ?: 0
        }
    }

    fun insertOrDeleteMark(mark: Mark): Boolean {
        return transaction(database) {
            if (!insertMark(mark)) {
                deleteMark(mark)
            } else {
                true
            }
        }
    }

    fun insertMarkDeleteOther(mark: Mark, otherIds: List<String>): Boolean {
        var haveDeleted: Boolean = false
        return transaction(database) {
            val insertAfterClean = mark !in this@LikesPluginLikesTable

            haveDeleted = deleteUserMarksOnMessage(
                mark.messageId,
                mark.userId,
                otherIds
            ) > 0

            if (insertAfterClean) {
                insertMark(mark)
                true
            } else {
                deleteMark(mark)
                false
            }
        }.also {
            if (it || haveDeleted) {
                LikesPluginLikesTableScope.launch {
                    messageButtonsUpdatedChannel.send(mark.messageId)
                }
            }
        }
    }

    fun userMarksOnMessage(messageId: MessageIdentifier, userId: Long): List<Mark> {
        return transaction(database) {
            select {
                messageIdColumn.eq(
                    messageId
                ).and(
                    userIdColumn.eq(
                        userId
                    )
                )
            }.map {
                Mark(
                    it.userId,
                    it.messageId,
                    it.buttonId
                )
            }
        }
    }

    fun marksOfMessage(messageId: MessageIdentifier): List<Mark> {
        return transaction(database) {
            select {
                messageIdColumn.eq(messageId)
            }.map {
                Mark(
                    it.userId,
                    it.messageId,
                    it.buttonId
                )
            }
        }
    }

    fun getMessageButtonMark(messageId: MessageIdentifier, buttonId: String): ButtonMark {
        return transaction(database) {
            select {
                messageIdColumn.eq(
                    messageId
                ).and(
                    buttonIdColumn.eq(buttonId)
                )
            }.count().let {
                count ->
                ButtonMark(
                    messageId,
                    buttonId,
                    count
                )
            }
        }
    }

    fun getMessageButtonMarks(messageId: MessageIdentifier): List<ButtonMark> {
        val mapOfButtonsCount = HashMap<String, Int>()

        transaction(database) {
            select {
                messageIdColumn.eq(
                    messageId
                )
            }.forEach {
                mapOfButtonsCount[it.buttonId] = (mapOfButtonsCount[it.buttonId] ?: 0) + 1
            }
        }

        return mapOfButtonsCount.map { (buttonId, count) ->
            ButtonMark(
                messageId,
                buttonId,
                count
            )
        }
    }
}