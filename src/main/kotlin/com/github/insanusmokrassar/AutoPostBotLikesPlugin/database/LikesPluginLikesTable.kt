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
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNull
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
    private val cancelDateTimeColumn = datetime("cancelDateTime").nullable()

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
            deleteMarkByMessageId(it)
        }
    }

    operator fun contains(mark: Mark): Boolean {
        return transaction(database) {
            select {
                makeSelectStatementExceptCancelled(mark)
            }.count() > 0
        }
    }

    private fun makeSelectStatementExceptCancelled(mark: Mark): Op<Boolean> {
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
        ).and(
            cancelDateTimeColumn.isNull()
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

    private fun delete(selectStatement: Op<Boolean>): Int = transaction(database) {
        update(
            { selectStatement.and(cancelDateTimeColumn.isNull()) }
        ) {
            it[cancelDateTimeColumn] = DateTime.now()
        }
    }

    private fun deleteMark(mark: Mark): Boolean {
        return (delete(makeSelectStatementExceptCancelled(mark)) > 0).also {
            if (it) {
                LikesPluginLikesTableScope.launch {
                    messageButtonsUpdatedChannel.send(mark.messageId)
                }
            }
        }
    }

    private fun deleteMarkByMessageId(messageId: MessageIdentifier): Boolean {
        return (delete(messageIdColumn.eq(messageId)) > 0).also {
            if (it) {
                LikesPluginLikesTableScope.launch {
                    messageButtonsUpdatedChannel.send(messageId)
                }
            }
        }
    }

    private fun deleteUserMarksOnMessage(messageId: MessageIdentifier, userId: Long, buttonIds: List<String>?): Int {
        val select = userIdColumn.eq(
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
        } ?: return 0
        return delete(select).also {
            if (it > 0) {
                LikesPluginLikesTableScope.launch {
                    messageButtonsUpdatedChannel.send(messageId)
                }
            }
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
        return transaction(database) {
            val insertAfterClean = mark !in this@LikesPluginLikesTable

            deleteUserMarksOnMessage(
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
            if (it) {
                LikesPluginLikesTableScope.launch {
                    messageButtonsUpdatedChannel.send(mark.messageId)
                }
            }
        }
    }

    fun userMarksOnMessage(messageId: MessageIdentifier, userId: Long, includeCancelled: Boolean = false): List<Mark> {
        val selectStatement = messageIdColumn.eq(
            messageId
        ).and(
            userIdColumn.eq(
                userId
            )
        ).let {
            if (!includeCancelled) {
                it.and(cancelDateTimeColumn.isNull())
            } else {
                it
            }
        }
        return transaction(database) {
            select { selectStatement }.map {
                Mark(
                    it.userId,
                    it.messageId,
                    it.buttonId
                )
            }
        }
    }

    fun userMarksOnMessage(messageId: MessageIdentifier, userId: Long): List<Mark> = userMarksOnMessage(messageId, userId, false)

    fun marksOfMessage(messageId: MessageIdentifier, includeCancelled: Boolean = false): List<Mark> {
        val selectStatement = messageIdColumn.eq(
            messageId
        ).let {
            if (!includeCancelled) {
                it.and(cancelDateTimeColumn.isNull())
            } else {
                it
            }
        }
        return transaction(database) {
            select { selectStatement }.map {
                Mark(
                    it.userId,
                    it.messageId,
                    it.buttonId
                )
            }
        }
    }

    fun marksOfMessage(messageId: MessageIdentifier) = marksOfMessage(messageId, false)

    fun getMessageButtonMark(messageId: MessageIdentifier, buttonId: String): ButtonMark {
        val selectStatement = messageIdColumn.eq(
            messageId
        ).and(
            buttonIdColumn.eq(buttonId)
        ).and(
            cancelDateTimeColumn.isNull()
        )
        return ButtonMark(
            messageId,
            buttonId,
            transaction(database) { select { selectStatement }.count() }
        )
    }

    fun getMessageButtonMarks(messageId: MessageIdentifier): List<ButtonMark> {
        val mapOfButtonsCount = HashMap<String, Int>()
        val selectStatement = messageIdColumn.eq(
            messageId
        ).and(
            cancelDateTimeColumn.isNull()
        )

        transaction(database) {
            select { selectStatement }.map { it.buttonId }
        }.forEach { buttonId ->
            mapOfButtonsCount[buttonId] = (mapOfButtonsCount[buttonId] ?: 0) + 1
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