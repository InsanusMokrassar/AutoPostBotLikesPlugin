package com.github.insanusmokrassar.AutoPostBotLikesPlugin

data class LikeConfig (
    val text: String?,
    private val identifier: String?,
    val markAnswer: String?,
    val unmarkAnswer: String?
) {
    val realIdentifier: String? by lazy {
        identifier ?: text
    }
}
