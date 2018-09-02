package com.github.insanusmokrassar.AutoPostBotLikesPlugin.old

import com.github.insanusmokrassar.AutoPostBotLikesPlugin.old.database.LikesPluginLikesTable
import com.github.insanusmokrassar.AutoPostBotLikesPlugin.old.database.LikesPluginRegisteredLikesMessagesTable
import com.github.insanusmokrassar.AutoPostBotLikesPlugin.old.listeners.*
import com.github.insanusmokrassar.AutoPostTelegramBot.base.models.FinalConfig
import com.github.insanusmokrassar.AutoPostTelegramBot.base.plugins.*
import com.github.insanusmokrassar.AutoPostTelegramBot.plugins.publishers.PostPublisher
import com.github.insanusmokrassar.IObjectK.interfaces.IObject
import com.github.insanusmokrassar.IObjectKRealisations.toObject
import com.pengrad.telegrambot.TelegramBot
import java.lang.ref.WeakReference

class LikesPlugin(
    config: IObject<Any>?
) : Plugin {
    private val config = config ?.toObject(LikePluginConfig::class.java) ?: LikePluginConfig()

    val likesPluginRegisteredLikesMessagesTable = LikesPluginRegisteredLikesMessagesTable()
    val likesPluginLikesTable = LikesPluginLikesTable(likesPluginRegisteredLikesMessagesTable)

    override fun onInit(bot: TelegramBot, baseConfig: FinalConfig, pluginManager: PluginManager) {
        val publisher = pluginManager.plugins.firstOrNull { it is PostPublisher } as? PostPublisher ?: return

        val botWR = WeakReference(bot)

        MessagePostedListener(
            publisher.postPublishedChannel,
            likesPluginRegisteredLikesMessagesTable,
            baseConfig.targetChatId,
            config.separateAlways,
            config.separatedText,
            botWR
        )

        LikesListener(baseConfig.targetChatId, likesPluginLikesTable, config.likeAnswer, botWR)
        config.dislikeAnswer ?.let {
            DislikesListener(baseConfig.targetChatId, likesPluginLikesTable, it, botWR)
        }

        RatingChangedListener(
            likesPluginLikesTable,
            likesPluginRegisteredLikesMessagesTable,
            botWR,
            baseConfig.targetChatId,
            config
        )
    }
}