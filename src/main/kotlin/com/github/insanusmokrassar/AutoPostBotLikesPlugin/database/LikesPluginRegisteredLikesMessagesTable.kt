package com.github.insanusmokrassar.AutoPostBotLikesPlugin.database

import kotlinx.coroutines.experimental.channels.BroadcastChannel
import kotlinx.coroutines.experimental.launch
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime

private const val broadcastCount = 256

typealias MessageIdToDateTime = Pair<Int, DateTime>

class LikesPluginRegisteredLikesMessagesTable : Table() {
    val messageIdAllocatedChannel = BroadcastChannel<Int>(broadcastCount)
    val messageIdRemovedChannel = BroadcastChannel<Int>(broadcastCount)

    private val messageId = integer("messageId").primaryKey()
    private val dateTime = datetime("datetime")

    init {
        transaction {
            SchemaUtils.createMissingTablesAndColumns(this@LikesPluginRegisteredLikesMessagesTable)
        }
    }

    operator fun contains(messageId: Int) : Boolean {
        return transaction {
            select { this@LikesPluginRegisteredLikesMessagesTable.messageId.eq(messageId) }.count() > 0
        }
    }

    fun registerMessageId(messageId: Int, dateTime: DateTime): Boolean {
        return (if (messageId !in this) {
            transaction {
                !insert {
                    it[this@LikesPluginRegisteredLikesMessagesTable.messageId] = messageId
                    it[this@LikesPluginRegisteredLikesMessagesTable.dateTime] = dateTime
                }.isIgnore
            }
        } else {
            false
        }).also {
            if (it) {
                launch {
                    messageIdAllocatedChannel.send(messageId)
                }
            }
        }
    }

    fun unregisterMessageId(messageId: Int): Boolean {
        return (if (messageId in this) {
            transaction {
                deleteWhere {
                    this@LikesPluginRegisteredLikesMessagesTable.messageId.eq(messageId)
                } > 0
            }
        } else {
            false
        }).also {
            if (it) {
                launch {
                    messageIdRemovedChannel.send(messageId)
                }
            }
        }
    }

    fun getAllRegistered(): List<MessageIdToDateTime> {
        return transaction {
            selectAll().map {
                it[messageId] to it[dateTime]
            }
        }
    }

    fun getBetweenDates(
        from: DateTime = DateTime(0L),
        to: DateTime = DateTime.now()
    ): List<MessageIdToDateTime> {
        return transaction {
            select {
                dateTime.between(from, to)
            }.map {
                it[messageId] to it[dateTime]
            }
        }
    }
}