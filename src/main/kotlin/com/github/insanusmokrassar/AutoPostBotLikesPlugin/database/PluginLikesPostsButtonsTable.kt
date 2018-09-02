package com.github.insanusmokrassar.AutoPostBotLikesPlugin.database

import org.jetbrains.exposed.sql.Table

class PluginLikesPostsButtonsTable : Table() {
    private val id = integer("id").primaryKey().autoIncrement()
    private val postId = integer("postId")
    private val identifier = text("identifier")


}