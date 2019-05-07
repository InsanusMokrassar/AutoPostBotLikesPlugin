package com.github.insanusmokrassar.AutoPostBotLikesPlugin

import com.github.insanusmokrassar.AutoPostBotLikesPlugin.database.*
import com.github.insanusmokrassar.AutoPostBotLikesPlugin.listeners.*
import com.github.insanusmokrassar.AutoPostBotLikesPlugin.models.config.*
import com.github.insanusmokrassar.AutoPostBotLikesPlugin.utils.extensions.AdminsHolder
import com.github.insanusmokrassar.AutoPostTelegramBot.base.models.FinalConfig
import com.github.insanusmokrassar.AutoPostTelegramBot.base.plugins.Plugin
import com.github.insanusmokrassar.AutoPostTelegramBot.base.plugins.PluginManager
import com.github.insanusmokrassar.AutoPostTelegramBot.plugins.publishers.PostPublisher
import com.github.insanusmokrassar.TelegramBotAPI.bot.RequestsExecutor
import kotlinx.serialization.*
import java.lang.ref.WeakReference

@Serializable
class LikesPlugin(
    val buttons: List<ButtonConfig>,
    private val groups: List<GroupConfig> = emptyList(),
    val separateAlways: Boolean = false,
    val separatedText: String = "Like? :)",
    val debounceDelay: Long = 500
) : Plugin {
    @Transient
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

    @Transient
    private val adaptedGroups: List<Group> by lazy {
        realGroups.map {
                group ->
            Group(
                group.radio,
                buttons.filter {
                    it.id in group.items
                }
            )
        }
    }

    @Transient
    val likesPluginRegisteredLikesMessagesTable = LikesPluginRegisteredLikesMessagesTable()
    @Transient
    val likesPluginLikesTable = LikesPluginLikesTable(likesPluginRegisteredLikesMessagesTable)
    @Transient
    val likesPluginMessagesTable = LikesPluginMessagesTable(likesPluginRegisteredLikesMessagesTable)

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
            botWR
        )

        LikesGroupsUpdater(
            likesPluginLikesTable,
            likesPluginRegisteredLikesMessagesTable,
            botWR,
            baseConfig.targetChatId,
            debounceDelay,
            adaptedGroups
        )

        adaptedGroups.map { group ->
            group.items.map { button ->
                enableMarksListener(
                    baseConfig.targetChatId,
                    likesPluginLikesTable,
                    button,
                    botWR,
                    group.other(button).map { it.id }
                )
            }
        }

        val adminsHolder = AdminsHolder(
            botWR,
            baseConfig.targetChatId
        )

        enableDetectLikesAttachmentMessages(
            adminsHolder,
            baseConfig.targetChatId,
            likesPluginMessagesTable,
            registrator,
            botWR
        )

        enableDetectLikesRefreshMessages(
            adminsHolder,
            baseConfig.targetChatId,
            likesPluginLikesTable,
            likesPluginRegisteredLikesMessagesTable,
            botWR
        )
    }
}