package com.github.insanusmokrassar.AutoPostBotLikesPlugin.database

import com.github.insanusmokrassar.TelegramBotAPI.types.MessageIdentifier
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction

class LikesPluginMessagesTable(
    private val likesPluginRegisteredLikesMessagesTable: LikesPluginRegisteredLikesMessagesTable = LikesPluginRegisteredLikesMessagesTable()
) : Table() {
    private val id = long("id").primaryKey().autoIncrement()
    private val likesId: Column<LikesGroupId> = long("likesId")
    private val messageId: Column<MessageIdentifier> = long("messageId").uniqueIndex()

    init {
        transaction {
            SchemaUtils.createMissingTablesAndColumns(this@LikesPluginMessagesTable)
        }
    }

    operator fun get(likesGroupId: LikesGroupId): List<MessageIdentifier> = transaction {
        select {
            likesId.eq(likesGroupId)
        }.map {
            it[messageId]
        }
    }

    operator fun contains(groupIdToMessageId: Pair<LikesGroupId, MessageIdentifier>): Boolean = transaction {
        select {
            likesId.eq(groupIdToMessageId.first).and(messageId.eq(groupIdToMessageId.second))
        }.firstOrNull() != null
    }

    operator fun contains(messageId: MessageIdentifier): Boolean = transaction {
        select {
            this@LikesPluginMessagesTable.messageId.eq(messageId)
        }.firstOrNull() != null
    }

    fun registerMessagesForLikeGroup(likesGroupId: LikesGroupId, messageIds: List<MessageIdentifier>): Boolean = transaction {
        val filtered = messageIds.filter {
            it !in this@LikesPluginMessagesTable
        }
        if (filtered.isEmpty()) {
            false
        } else {
            var atLeastOneRegistered = false
            filtered.forEach { messageIdentifier ->
                atLeastOneRegistered = atLeastOneRegistered || insert {
                    it[likesId] = likesGroupId
                    it[messageId] = messageIdentifier
                }[id] != null
            }
            return@transaction atLeastOneRegistered
        }
    }.also {
        if (it) {
            likesPluginRegisteredLikesMessagesTable.registerMessageId(likesGroupId)
        }
    }

    fun getLikesGroupId(messageId: MessageIdentifier): LikesGroupId? = transaction {
        select {
            this@LikesPluginMessagesTable.messageId.eq(messageId)
        }.firstOrNull() ?.get(likesId)
    }
}