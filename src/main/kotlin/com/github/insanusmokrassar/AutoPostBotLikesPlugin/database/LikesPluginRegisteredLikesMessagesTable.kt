package com.github.insanusmokrassar.AutoPostBotLikesPlugin.database

import com.github.insanusmokrassar.AutoPostTelegramBot.largeBroadcastCapacity
import com.github.insanusmokrassar.AutoPostTelegramBot.utils.NewDefaultCoroutineScope
import com.github.insanusmokrassar.TelegramBotAPI.types.MessageIdentifier
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.launch
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime

typealias MessageIdToDateTime = Pair<LikesGroupId, DateTime>
typealias LikesGroupId = MessageIdentifier

private val LikesPluginRegisteredLikesMessagesTableScope = NewDefaultCoroutineScope(1)

class LikesPluginRegisteredLikesMessagesTable: Table() {
    val messageIdAllocatedChannel = BroadcastChannel<MessageIdentifier>(largeBroadcastCapacity)
    val messageIdRemovedChannel = BroadcastChannel<MessageIdentifier>(largeBroadcastCapacity)

    private val messageId: Column<LikesGroupId> = long("messageId").primaryKey()
    private val dateTime = datetime("datetime")

    init {
        transaction {
            SchemaUtils.createMissingTablesAndColumns(this@LikesPluginRegisteredLikesMessagesTable)
        }
    }

    operator fun contains(messageId: LikesGroupId) : Boolean {
        return transaction {
            select { this@LikesPluginRegisteredLikesMessagesTable.messageId.eq(messageId) }.count() > 0
        }
    }

    internal fun registerMessageId(messageId: LikesGroupId): Boolean {
        return (transaction {
            if (messageId !in this@LikesPluginRegisteredLikesMessagesTable) {
                insert {
                    it[this@LikesPluginRegisteredLikesMessagesTable.messageId] = messageId
                    it[this@LikesPluginRegisteredLikesMessagesTable.dateTime] = DateTime.now()
                }
                true
            } else {
                false
            }
        }).also {
            if (it) {
                LikesPluginRegisteredLikesMessagesTableScope.launch {
                    messageIdAllocatedChannel.send(messageId)
                }
            }
        }
    }

    fun unregisterMessageId(messageId: LikesGroupId): Boolean {
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
                LikesPluginRegisteredLikesMessagesTableScope.launch {
                    messageIdRemovedChannel.send(messageId)
                }
            }
        }
    }

    fun getAllRegistered(): List<MessageIdToDateTime> {
        return transaction {
            selectAll().map {
                MessageIdToDateTime(
                    it[messageId],
                    it[dateTime]
                )
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