package com.github.insanusmokrassar.AutoPostBotLikesPlugin.database

import com.github.insanusmokrassar.AutoPostBotLikesPlugin.models.UserMark
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime

class PluginLikesLikesTable: Table() {
    private val id = integer("id").primaryKey().autoIncrement()

    private val postId = integer("postId")
    private val chatId = long("chatId")
    private val mark = text("mark")
    private val dateTime = datetime("markDateTime").clientDefault {
        DateTime.now()
    }

    init {
        transaction {
            SchemaUtils.createMissingTablesAndColumns(this@PluginLikesLikesTable)
        }
    }

    @Throws(IllegalStateException::class)
    fun plus(userMark: UserMark, radioMode: Boolean = true): UserMark? {
        return transaction {
            (if (userMark in this@PluginLikesLikesTable) {
                get(userMark)
            } else {
                insert {
                    it[postId] = userMark.postId
                    it[chatId] = userMark.chatId
                    it[mark] = userMark.mark
                    it[dateTime] = userMark.dateTime
                }.run {
                    UserMark(
                        get(postId) ?: return@run null,
                        get(chatId) ?: return@run null,
                        get(mark) ?: return@run null,
                        get(dateTime) ?: return@run null,
                        get(id) ?: return@run null
                    )
                }
            }) ?.also {
                if (radioMode) {
                    deleteWhere {
                        postId.eq(
                            it.postId
                        ).and(
                            chatId.eq(it.chatId)
                        ).run {
                            it.id ?.let {
                                markId ->
                                id.neq(markId)
                            } ?.let {
                                expression ->
                                and(expression)
                            } ?: this
                        }
                    }
                }
            }
        }
    }

    operator fun minus(userMark: UserMark): Boolean {
        return transaction {
            deleteWhere {
                postId.eq(
                    userMark.postId
                ).and(
                    chatId.eq(userMark.chatId)
                ).and(
                    mark.eq(userMark.mark)
                )
            } > 0
        }
    }

    operator fun get(id: Int): UserMark? {
        return transaction {
            select {
                this@PluginLikesLikesTable.id.eq(id)
            }.firstOrNull() ?.run {
                UserMark(
                    get(postId),
                    get(chatId),
                    get(mark),
                    get(dateTime),
                    get(this@PluginLikesLikesTable.id)
                )
            }
        }
    }

    operator fun get(userMark: UserMark): UserMark? {
        return transaction {
            select {
                postId.eq(
                    userMark.postId
                ).and(
                    chatId.eq(userMark.chatId)
                ).and(
                    mark.eq(userMark.mark)
                )
            }.firstOrNull() ?.run {
                UserMark(
                    get(postId),
                    get(chatId),
                    get(mark),
                    get(dateTime),
                    get(this@PluginLikesLikesTable.id)
                )
            }
        }
    }

    operator fun contains(id: Int): Boolean {
        return transaction {
            select {
                this@PluginLikesLikesTable.id.eq(id)
            }.count() > 0
        }
    }

    operator fun contains(userMark: UserMark): Boolean {
        return transaction {
            select {
                postId.eq(
                    userMark.postId
                ).and(
                    chatId.eq(userMark.chatId)
                ).and(
                    mark.eq(userMark.mark)
                )
            }.count() > 0
        }
    }

    fun getUserMarks(chatId: Long): List<UserMark> {
        return transaction {
            select {
                this@PluginLikesLikesTable.chatId.eq(chatId)
            }.map {
                UserMark(
                    it[postId],
                    it[this@PluginLikesLikesTable.chatId],
                    it[mark],
                    it[dateTime],
                    it[id]
                )
            }
        }
    }

    fun getPostMarks(postId: Int): List<UserMark> {
        return transaction {
            select {
                this@PluginLikesLikesTable.postId.eq(postId)
            }.map {
                UserMark(
                    it[this@PluginLikesLikesTable.postId],
                    it[chatId],
                    it[mark],
                    it[dateTime],
                    it[id]
                )
            }
        }
    }
}