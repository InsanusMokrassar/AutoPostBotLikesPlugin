package com.github.insanusmokrassar.AutoPostBotLikesPlugin.database

import com.github.insanusmokrassar.AutoPostTelegramBot.utils.extensions.subscribe
import kotlinx.coroutines.experimental.channels.BroadcastChannel
import kotlinx.coroutines.experimental.launch
import org.h2.jdbc.JdbcSQLException
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction

typealias MessageIdRatingPair = Pair<Int, Int>
typealias MessageIdUserId = Pair<Int, Long>

private const val countOfSubscriptions = 256

private const val resultColumnName = "result"

class LikesPluginLikesTable(
    private val likesPluginRegisteredLikesMessagesTable: LikesPluginRegisteredLikesMessagesTable
) : Table() {
    val likesChannel = BroadcastChannel<MessageIdUserId>(countOfSubscriptions)
    val dislikesChannel = BroadcastChannel<MessageIdUserId>(countOfSubscriptions)
    val ratingsChannel = BroadcastChannel<MessageIdRatingPair>(countOfSubscriptions)

    private val userId = long("userId").primaryKey()
    private val messageId = integer("messageId").primaryKey()
    private val like = bool("like").default(false)

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

    fun userLikePost(userId: Long, messageId: Int) {
        userLike(userId, messageId, true)

        launch {
            likesChannel.send(messageId to userId)
        }
    }

    fun userDislikePost(userId: Long, messageId: Int) {
        userLike(userId, messageId, false)

        launch {
            dislikesChannel.send(messageId to userId)
        }
    }

    fun postLikes(messageId: Int): Int = postLikeCount(messageId, true)

    fun postDislikes(messageId: Int): Int = postLikeCount(messageId, false)

    fun getPostRating(messageId: Int): Int {
        return transaction {
            try {
                exec("SELECT (likes-dislikes) as $resultColumnName FROM " +
                    "(SELECT count(*) as likes FROM ${nameInDatabaseCase()} WHERE ${this@LikesPluginLikesTable.messageId.name}=$messageId AND \"${like.name}\"=${like.columnType.valueToString(true)}), " +
                    "(SELECT count(*) as dislikes FROM ${nameInDatabaseCase()} WHERE ${this@LikesPluginLikesTable.messageId.name}=$messageId AND \"${like.name}\"=${like.columnType.valueToString(false)});") {
                    if (it.first()) {
                        it.getInt(it.findColumn(resultColumnName))
                    } else {
                        0
                    }
                } ?: 0
            } catch (e: JdbcSQLException) {
                select {
                    createChooser(messageId, like = true)
                }.count() - select {
                    createChooser(messageId, like = false)
                }.count()
            }
        }
    }

    /**
     * @param min Included. If null - always true
     * @param max Included. If null - always true
     *
     * @return Pairs with messageId to Rate
     */
    fun getRateRange(min: Int?, max: Int?): List<MessageIdRatingPair> {
        return likesPluginRegisteredLikesMessagesTable.getAllRegistered().map {
            it to getPostRating(it)
        }.sortedByDescending {
            it.second
        }.filter {
            pair ->
            min ?.let { it <= pair.second } != false && max ?.let { pair.second <= it } != false
        }
    }

    private fun postLikeCount(messageId: Int, like: Boolean): Int = transaction {
        select {
            this@LikesPluginLikesTable.messageId.eq(messageId).and(this@LikesPluginLikesTable.like.eq(like))
        }.count()
    }

    private fun createChooser(messageId: Int, userId: Long? = null, like: Boolean? = null): Op<Boolean> {
        return this@LikesPluginLikesTable.messageId.eq(messageId).let {
            userId ?.let {
                userId ->
                it.and(this@LikesPluginLikesTable.userId.eq(userId))
            } ?: it
        }.let {
            like ?. let {
                like ->
                it.and(this@LikesPluginLikesTable.like.eq(like))
            } ?: it
        }
    }

    private fun userLike(userId: Long, messageId: Int, like: Boolean) {
        val chooser = createChooser(messageId, userId)
        transaction {
            val record = select {
                chooser
            }.firstOrNull()
            record ?.let {
                if (it[this@LikesPluginLikesTable.like] == like) {
                    deleteWhere { chooser }
                } else {
                    update(
                        {
                            chooser
                        }
                    ) {
                        it[this@LikesPluginLikesTable.like] = like
                    }
                }
            } ?:let {
                addUser(userId, messageId, like)
            }
            launch {
                ratingsChannel.send(
                    MessageIdRatingPair(messageId, getPostRating(messageId))
                )
            }
        }
    }

    private fun addUser(userId: Long, messageId: Int, like: Boolean) {
        transaction {
            insert {
                it[this@LikesPluginLikesTable.messageId] = messageId
                it[this@LikesPluginLikesTable.userId] = userId
                it[this@LikesPluginLikesTable.like] = like
            }
        }
    }

    internal fun clearPostMarks(messageId: Int) {
        transaction {
            deleteWhere { this@LikesPluginLikesTable.messageId.eq(messageId) }
        }
    }
}