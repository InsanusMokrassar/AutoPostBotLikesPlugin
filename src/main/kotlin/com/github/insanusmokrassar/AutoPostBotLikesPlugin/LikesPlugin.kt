package com.github.insanusmokrassar.AutoPostBotLikesPlugin

import com.github.insanusmokrassar.AutoPostBotLikesPlugin.database.*
import com.github.insanusmokrassar.AutoPostBotLikesPlugin.listeners.*
import com.github.insanusmokrassar.AutoPostBotLikesPlugin.models.config.*
import com.github.insanusmokrassar.AutoPostBotLikesPlugin.utils.extensions.AdminsHolder
import com.github.insanusmokrassar.AutoPostTelegramBot.base.models.DatabaseConfig
import com.github.insanusmokrassar.AutoPostTelegramBot.base.models.FinalConfig
import com.github.insanusmokrassar.AutoPostTelegramBot.base.plugins.Plugin
import com.github.insanusmokrassar.AutoPostTelegramBot.base.plugins.PluginManager
import com.github.insanusmokrassar.AutoPostTelegramBot.plugins.publishers.PostPublisher
import com.github.insanusmokrassar.TelegramBotAPI.bot.RequestsExecutor
import kotlinx.coroutines.*
import kotlinx.serialization.*
import java.lang.ref.WeakReference
import java.util.concurrent.Executors

@Serializable
class LikesPlugin(
    val buttons: List<ButtonConfig>,
    val databaseConfig: DatabaseConfig,
    private val groups: List<GroupConfig> = emptyList(),
    val separateAlways: Boolean = false,
    val separatedText: String = "Like? :)",
    val debounceDelay: Long = 500
) : Plugin {
    @Transient
    private val scope = CoroutineScope(Executors.newCachedThreadPool().asCoroutineDispatcher())

    private val realGroups: List<GroupConfig> by lazy {
        if (groups.isEmpty()) {
            listOf(
                GroupConfig(
                    items = buttons.map { it.id }
                )
            )
        } else {
            groups
        }
    }

    private val adaptedGroups: List<Group> by lazy {
        realGroups.map { group ->
            Group(
                group.radio,
                buttons.filter {
                    it.id in group.items
                }
            )
        }
    }

    @Transient
    private val database = databaseConfig.connect()

    @Transient
    val likesPluginRegisteredLikesMessagesTable = LikesPluginRegisteredLikesMessagesTable(database)
    @Transient
    val likesPluginLikesTable = LikesPluginLikesTable(likesPluginRegisteredLikesMessagesTable, database)
    @Transient
    val likesPluginMessagesTable = LikesPluginMessagesTable(database, likesPluginRegisteredLikesMessagesTable)

    override suspend fun onInit(executor: RequestsExecutor, baseConfig: FinalConfig, pluginManager: PluginManager) {
        super.onInit(executor, baseConfig, pluginManager)
        val publisher = pluginManager.plugins.firstOrNull { it is PostPublisher } as? PostPublisher ?: return

        val botWR = WeakReference(executor)

        val registrator = LikesGroupsRegistrator(
            publisher.postPublishedChannel,
            likesPluginMessagesTable,
            baseConfig.targetChatId,
            separateAlways,
            separatedText,
            botWR,
            scope
        )

        LikesGroupsUpdater(
            likesPluginLikesTable,
            likesPluginRegisteredLikesMessagesTable,
            botWR,
            baseConfig.targetChatId,
            debounceDelay,
            adaptedGroups,
            scope
        )

        scope.enableMarksListener(
            baseConfig.targetChatId,
            likesPluginLikesTable,
            adaptedGroups.flatMap { it.items },
            adaptedGroups.mapNotNull { group ->
                if (group.isRadio) {
                    group.items.map { groupItem -> groupItem to group.items.map { it.id } }
                } else {
                    null
                }
            }.flatten().toMap(),
            botWR
        )

        val adminsHolder = AdminsHolder(
            botWR,
            baseConfig.targetChatId
        )

        scope.enableDetectLikesAttachmentMessages(
            adminsHolder,
            baseConfig.targetChatId,
            likesPluginMessagesTable,
            registrator,
            botWR
        )

        scope.enableDetectLikesRefreshMessages(
            adminsHolder,
            baseConfig.targetChatId,
            likesPluginLikesTable,
            likesPluginRegisteredLikesMessagesTable,
            botWR
        )
    }
}