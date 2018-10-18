package com.github.insanusmokrassar.AutoPostBotLikesPlugin.database

import com.github.insanusmokrassar.AutoPostBotLikesPlugin.models.ButtonMark
import com.github.insanusmokrassar.AutoPostBotLikesPlugin.models.Mark
import com.github.insanusmokrassar.AutoPostTelegramBot.utils.extensions.subscribe
import kotlinx.coroutines.experimental.channels.BroadcastChannel
import kotlinx.coroutines.experimental.launch
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime

private const val countOfSubscriptions = 256

class LikesPluginLikesTable(
    likesPluginRegisteredLikesMessagesTable: LikesPluginRegisteredLikesMessagesTable
) : Table() {
    val messageButtonsUpdatedChannel = BroadcastChannel<Int>(countOfSubscriptions)

    private val id = integer("id").primaryKey().autoIncrement()
    private val userId = long("userId")
    private val messageId = integer("messageId")
    private val buttonId = text("buttonId")
    private val dateTime = datetime("markDateTime").default(DateTime.now()).clientDefault {
        DateTime.now()
    }

    private val ResultRow.buttonId: String
        get() = get(this@LikesPluginLikesTable.buttonId)

    private val ResultRow.messageId: Int
        get() = get(this@LikesPluginLikesTable.messageId)

    private val ResultRow.userId: Long
        get() = get(this@LikesPluginLikesTable.userId)

    init {
        transaction {
            SchemaUtils.createMissingTablesAndColumns(this@LikesPluginLikesTable)
        }
        likesPluginRegisteredLikesMessagesTable.messageIdRemovedChannel.subscribe {
            transaction {
                deleteWhere {
                    messageId.eq(it)
                }
            }
        }
    }

    operator fun contains(mark: Mark): Boolean {
        return transaction {
            select {
                makeSelectStatement(mark)
            }.count() > 0
        }
    }

    private fun makeSelectStatement(mark: Mark): Op<Boolean> {
        return userId.eq(
            mark.userId
        ).and(
            messageId.eq(
                mark.messageId
            )
        ).and(
            buttonId.eq(
                mark.buttonId
            )
        )
    }

    private fun insertMark(mark: Mark): Boolean {
        return transaction {
            if (contains(mark)) {
                false
            } else {
                insert {
                    it[userId] = mark.userId
                    it[messageId] = mark.messageId
                    it[buttonId] = mark.buttonId
                }[id] != null
            }
        }.also {
            if (it) {
                launch {
                    messageButtonsUpdatedChannel.send(mark.messageId)
                }
            }
        }
    }

    private fun deleteMark(mark: Mark): Boolean {
        return transaction {
            deleteWhere {
                makeSelectStatement(mark)
            } > 0
        }.also {
            if (it) {
                launch {
                    messageButtonsUpdatedChannel.send(mark.messageId)
                }
            }
        }
    }

    private fun deleteUserMarksOnMessage(messageId: Int, userId: Long, buttonIds: List<String>?): Int {
        return transaction {
            this@LikesPluginLikesTable.userId.eq(
                userId
            ).and(
                this@LikesPluginLikesTable.messageId.eq(
                    messageId
                )
            ).let {
                buttonIds ?.let {
                    buttonIds ->
                    it.and(
                        buttonId.inList(buttonIds)
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
        return transaction {
            if (!insertMark(mark)) {
                deleteMark(mark)
            } else {
                true
            }
        }
    }

    fun insertMarkDeleteOther(mark: Mark, otherIds: List<String>): Boolean {
        return transaction {
            val insertAfterClean = mark !in this@LikesPluginLikesTable

            val haveDeleted = deleteUserMarksOnMessage(
                mark.messageId,
                mark.userId,
                otherIds
            ) > 0

            (if (insertAfterClean) {
                insertMark(mark)
                true
            } else {
                deleteMark(mark)
                false
            }).also {
                if (it || haveDeleted) {
                    launch {
                        messageButtonsUpdatedChannel.send(mark.messageId)
                    }
                }
            }
        }
    }

    fun userMarksOnMessage(messageId: Int, userId: Long): List<Mark> {
        return transaction {
            select {
                this@LikesPluginLikesTable.messageId.eq(
                    messageId
                ).and(
                    this@LikesPluginLikesTable.userId.eq(
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

    fun marksOfMessage(messageId: Int): List<Mark> {
        return transaction {
            select {
                this@LikesPluginLikesTable.messageId.eq(messageId)
            }.map {
                Mark(
                    it.userId,
                    it.messageId,
                    it.buttonId
                )
            }
        }
    }

    fun getMessageButtonMark(messageId: Int, buttonId: String): ButtonMark {
        return transaction {
            select {
                this@LikesPluginLikesTable.messageId.eq(
                    messageId
                ).and(
                    this@LikesPluginLikesTable.buttonId.eq(buttonId)
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

    fun getMessageButtonMarks(messageId: Int): List<ButtonMark> {
        val mapOfButtonsCount = HashMap<String, Int>()

        return transaction {
            select {
                this@LikesPluginLikesTable.messageId.eq(
                    messageId
                )
            }.forEach {
                mapOfButtonsCount[it.buttonId] = mapOfButtonsCount[it.buttonId] ?.plus(1) ?: 1
            }
            mapOfButtonsCount.map {
                (buttonId, count) ->
                ButtonMark(
                    messageId,
                    buttonId,
                    count
                )
            }
        }
    }
}