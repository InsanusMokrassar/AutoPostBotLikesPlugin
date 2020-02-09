package com.github.insanusmokrassar.AutoPostBotLikesPlugin.database

import com.github.insanusmokrassar.TelegramBotAPI.types.MediaGroupIdentifier
import com.github.insanusmokrassar.TelegramBotAPI.types.MessageIdentifier
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction

class LikesPluginMessagesTable(
    private val database: Database,
    private val likesPluginRegisteredLikesMessagesTable: LikesPluginRegisteredLikesMessagesTable = LikesPluginRegisteredLikesMessagesTable(database)
) : Table() {
    private val idColumn = long("id").primaryKey().autoIncrement()
    private val likesIdColumn: Column<LikesGroupId> = long("likesId")
    private val messageIdColumn: Column<MessageIdentifier> = long("messageId").uniqueIndex()
    private val groupIdColumn: Column<MediaGroupIdentifier?> = text("groupId").nullable()

    init {
        transaction(database) {
            SchemaUtils.createMissingTablesAndColumns(this@LikesPluginMessagesTable)
        }
    }

    operator fun get(likesGroupId: LikesGroupId): List<Pair<MessageIdentifier, MediaGroupIdentifier?>> = transaction(database) {
        select {
            likesIdColumn.eq(likesGroupId)
        }.asSequence().map {
            it[messageIdColumn] to it[groupIdColumn]
        }.sortedBy {
            it.first
        }.toList()
    }

    operator fun contains(groupIdToMessageId: Pair<LikesGroupId, MessageIdentifier>): Boolean = transaction(database) {
        select {
            likesIdColumn.eq(groupIdToMessageId.first).and(messageIdColumn.eq(groupIdToMessageId.second))
        }.firstOrNull() != null
    }

    operator fun contains(messageId: MessageIdentifier): Boolean = transaction(database) {
        select {
            messageIdColumn.eq(messageId)
        }.firstOrNull() != null
    }

    fun registerMessagesForLikeGroup(
        likesGroupId: LikesGroupId,
        messageIds: List<MessageIdentifier>,
        mediaGroups: Map<MessageIdentifier, MediaGroupIdentifier>/* = emptyMap()*/
    ): Boolean = transaction(database) {
        val realMessagesIds = if (likesGroupId !in messageIds) {
            (messageIds + likesGroupId).sorted()
        } else {
            messageIds
        }
        val filtered = realMessagesIds.filter {
            it !in this@LikesPluginMessagesTable
        }
        if (filtered.isEmpty()) {
            false
        } else {
            var atLeastOneRegistered = false
            filtered.forEach { messageIdentifier ->
                atLeastOneRegistered = insert {
                    it[likesIdColumn] = likesGroupId
                    it[messageIdColumn] = messageIdentifier
                    it[groupIdColumn] = mediaGroups[messageIdentifier]
                }[idColumn] != null || atLeastOneRegistered
            }
            return@transaction atLeastOneRegistered
        }
    }.also {
        if (it) {
            likesPluginRegisteredLikesMessagesTable.registerMessageId(likesGroupId)
        }
    }

    fun getLikesGroupId(messageId: MessageIdentifier): LikesGroupId? = transaction(database) {
        select {
            messageIdColumn.eq(messageId)
        }.firstOrNull() ?.get(likesIdColumn)
    }
}