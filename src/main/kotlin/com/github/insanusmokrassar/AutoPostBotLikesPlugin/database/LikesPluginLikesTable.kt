package dev.inmo.AutoPostBotLikesPlugin.database

import dev.inmo.AutoPostBotLikesPlugin.models.ButtonMark
import dev.inmo.AutoPostBotLikesPlugin.models.Mark
import dev.inmo.AutoPostTelegramBot.utils.NewDefaultCoroutineScope
import dev.inmo.AutoPostTelegramBot.utils.flow.collectWithErrors
import dev.inmo.tgbotapi.types.MessageIdentifier
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.launch
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.jodatime.datetime
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime

private val LikesPluginLikesTableScope = NewDefaultCoroutineScope(1)

class LikesPluginLikesTable(
    likesPluginRegisteredLikesMessagesTable: LikesPluginRegisteredLikesMessagesTable,
    private val database: Database
) : Table() {
    val messageButtonsUpdatedChannel = BroadcastChannel<MessageIdentifier>(Channel.CONFLATED)

    private val idColumn = integer("id").primaryKey().autoIncrement()
    private val userIdColumn = long("userId").index()
    private val messageIdColumn = long("messageId").index()
    private val buttonIdColumn = text("buttonId")
    private val dateTimeColumn = datetime("markDateTime").default(DateTime.now())
    private val cancelDateTimeColumn = datetime("cancelDateTime").nullable()

    private val ResultRow.buttonId: String
        get() = get(buttonIdColumn)

    private val ResultRow.messageId: MessageIdentifier
        get() = get(messageIdColumn)

    private val ResultRow.userId: Long
        get() = get(userIdColumn)

    private val ResultRow.asMark: Mark
        get() = Mark(
            userId,
            messageId,
            buttonId
        )

    init {
        transaction(database) {
            SchemaUtils.createMissingTablesAndColumns(this@LikesPluginLikesTable)
        }
        LikesPluginLikesTableScope.launch {
            likesPluginRegisteredLikesMessagesTable.messageIdRemovedChannel.asFlow().collectWithErrors { messageId ->
                deleteMarkByMessageId(messageId).also {
                    if (it) {
                        LikesPluginLikesTableScope.launch {
                            messageButtonsUpdatedChannel.send(messageId)
                        }
                    }
                }
            }
        }
    }

    operator fun contains(mark: Mark): Boolean {
        val selectStatement = makeSelectStatementExceptCancelled(mark)
        return transaction(database) {
            select(selectStatement).limit(1).count() > 0
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
        ).and { cancelDateTimeColumn.isNull() }
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
        return delete(makeSelectStatementExceptCancelled(mark)) > 0
    }

    private fun deleteMarkByMessageId(messageId: MessageIdentifier): Boolean {
        return (delete(messageIdColumn.eq(messageId)) > 0)
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
        return delete(select)
    }

    fun insertOrDeleteMark(mark: Mark): Boolean {
        return transaction(database) {
            if (!insertMark(mark)) {
                deleteMark(mark)
            } else {
                true
            }
        }.also {
            LikesPluginLikesTableScope.launch {
                messageButtonsUpdatedChannel.send(mark.messageId)
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
            LikesPluginLikesTableScope.launch {
                messageButtonsUpdatedChannel.send(mark.messageId)
            }
        }
    }

    fun userMarksOnMessageIncludeCancelled(messageId: MessageIdentifier, userId: Long): List<Mark> {
        val selectStatement = messageIdColumn.eq(
            messageId
        ).and(
            userIdColumn.eq(
                userId
            )
        )
        return transaction(database) {
            select(selectStatement).map {
                it.asMark
            }
        }
    }

    fun userMarksOnMessage(messageId: MessageIdentifier, userId: Long): List<Mark> {
        val selectStatement = messageIdColumn.eq(
            messageId
        ).and(
            userIdColumn.eq(
                userId
            )
        ).and { cancelDateTimeColumn.isNull() }
        return transaction(database) {
            select(selectStatement).map {
                it.asMark
            }
        }
    }

    fun marksOfMessageIncludeCancelled(messageId: MessageIdentifier): List<Mark> {
        val selectStatement = messageIdColumn.eq(
            messageId
        )
        return transaction(database) {
            select(selectStatement).map {
                it.asMark
            }
        }
    }

    fun marksOfMessage(messageId: MessageIdentifier): List<Mark> {
        val selectStatement = messageIdColumn.eq(
            messageId
        ).and { cancelDateTimeColumn.isNull() }
        return transaction(database) {
            select(selectStatement).map {
                it.asMark
            }
        }
    }

    fun getMessageButtonMark(messageId: MessageIdentifier, buttonId: String): ButtonMark {
        val selectStatement = messageIdColumn.eq(
            messageId
        ).and(
            buttonIdColumn.eq(buttonId)
        ).and { cancelDateTimeColumn.isNull() }
        return ButtonMark(
            messageId,
            buttonId,
            transaction(database) { select (selectStatement).count() }.toInt()
        )
    }

    fun getMessageButtonMarks(messageId: MessageIdentifier): List<ButtonMark> {
        val selectStatement = messageIdColumn.eq(
            messageId
        ).and { cancelDateTimeColumn.isNull() }

        val mapOfButtonsCount = mutableMapOf<String, Int>()
        transaction(database) {
            select(selectStatement).map { it.buttonId }
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

    fun getMarksInDateTimeRange(from: DateTime? = null, to: DateTime? = null): List<Mark> {
        val selectStatement: SqlExpressionBuilder.() -> Op<Boolean> = {
            from ?.let { _ ->
                to ?.let { _ ->
                    dateTimeColumn.between(from, to)
                } ?: dateTimeColumn.greaterEq(from)
            } ?: to ?.let { _ ->
                dateTimeColumn.lessEq(to)
            } ?.and(
                cancelDateTimeColumn.isNull()
            ) ?: cancelDateTimeColumn.isNull()
        }
        return transaction(database) {
            select(selectStatement).map { it.asMark }
        }
    }
}