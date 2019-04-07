package com.github.insanusmokrassar.AutoPostBotLikesPlugin.database

import com.github.insanusmokrassar.TelegramBotAPI.types.MediaGroupIdentifier
import com.github.insanusmokrassar.TelegramBotAPI.types.MessageIdentifier
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction

class LikesPluginMessagesTable(
    private val likesPluginRegisteredLikesMessagesTable: LikesPluginRegisteredLikesMessagesTable = LikesPluginRegisteredLikesMessagesTable()
) : Table() {
    private val id = long("id").primaryKey().autoIncrement()
    private val likesId: Column<LikesGroupId> = long("likesId")
    private val messageId: Column<MessageIdentifier> = long("messageId").uniqueIndex()
    private val groupId: Column<MediaGroupIdentifier?> = text("groupId").nullable()

    init {
        transaction {
            SchemaUtils.createMissingTablesAndColumns(this@LikesPluginMessagesTable)
        }
    }

    operator fun get(likesGroupId: LikesGroupId): List<Pair<MessageIdentifier, MediaGroupIdentifier?>> = transaction {
        select {
            likesId.eq(likesGroupId)
        }.asSequence().map {
            it[messageId] to it[groupId]
        }.sortedBy {
            it.first
        }.toList()
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

    fun registerMessagesForLikeGroup(
        likesGroupId: LikesGroupId,
        messageIds: List<MessageIdentifier>,
        mediaGroups: Map<MessageIdentifier, MediaGroupIdentifier>/* = emptyMap()*/
    ): Boolean = transaction {
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
                    it[likesId] = likesGroupId
                    it[messageId] = messageIdentifier
                    it[groupId] = mediaGroups[messageIdentifier]
                }[id] != null || atLeastOneRegistered
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