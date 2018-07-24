package com.github.insanusmokrassar.AutoPostBotLikesPlugin.database

import kotlinx.coroutines.experimental.channels.BroadcastChannel
import kotlinx.coroutines.experimental.launch
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction

private const val broadcastCount = 256

class LikesPluginRegisteredLikesMessagesTable : Table() {
    val messageIdAllocatedChannel = BroadcastChannel<Int>(broadcastCount)
    val messageIdRemovedChannel = BroadcastChannel<Int>(broadcastCount)

    private val messageId = integer("messageId")

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

    fun registerMessageId(messageId: Int): Boolean {
        return (if (messageId !in this) {
            transaction {
                !insert {
                    it[this@LikesPluginRegisteredLikesMessagesTable.messageId] = messageId
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

    fun getAllRegistered(): List<Int> {
        return transaction {
            selectAll().map {
                it[messageId]
            }
        }
    }
}