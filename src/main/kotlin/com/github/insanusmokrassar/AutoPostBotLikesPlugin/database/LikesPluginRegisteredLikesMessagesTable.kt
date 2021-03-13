package dev.inmo.AutoPostBotLikesPlugin.database

import dev.inmo.AutoPostTelegramBot.utils.NewDefaultCoroutineScope
import dev.inmo.tgbotapi.types.MessageIdentifier
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.jodatime.datetime
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime

typealias MessageIdToDateTime = Pair<LikesGroupId, DateTime>
typealias LikesGroupId = MessageIdentifier

private val LikesPluginRegisteredLikesMessagesTableScope = NewDefaultCoroutineScope(1)

class LikesPluginRegisteredLikesMessagesTable(
    private val database: Database
): Table() {
    val messageIdAllocatedChannel = BroadcastChannel<MessageIdentifier>(Channel.CONFLATED)
    val messageIdRemovedChannel = BroadcastChannel<MessageIdentifier>(Channel.CONFLATED)

    private val messageIdColumn: Column<LikesGroupId> = long("messageId").primaryKey()
    private val dateTimeColumn = datetime("datetime")

    init {
        transaction(database) {
            SchemaUtils.createMissingTablesAndColumns(this@LikesPluginRegisteredLikesMessagesTable)
        }
    }

    operator fun contains(messageId: LikesGroupId) : Boolean {
        return transaction(database) {
            select { messageIdColumn.eq(messageId) }.count() > 0
        }
    }

    internal fun registerMessageId(messageId: LikesGroupId): Boolean {
        return (transaction(database) {
            if (messageId !in this@LikesPluginRegisteredLikesMessagesTable) {
                insert {
                    it[messageIdColumn] = messageId
                    it[dateTimeColumn] = DateTime.now()
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
            transaction(database) {
                deleteWhere {
                    messageIdColumn.eq(messageId)
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
        return transaction(database) {
            selectAll().map {
                MessageIdToDateTime(
                    it[messageIdColumn],
                    it[dateTimeColumn]
                )
            }
        }
    }

    fun getBetweenDates(
        from: DateTime = DateTime(0L),
        to: DateTime = DateTime.now()
    ): List<MessageIdToDateTime> {
        return transaction(database) {
            select {
                dateTimeColumn.between(from, to)
            }.map {
                it[messageIdColumn] to it[dateTimeColumn]
            }
        }
    }
}