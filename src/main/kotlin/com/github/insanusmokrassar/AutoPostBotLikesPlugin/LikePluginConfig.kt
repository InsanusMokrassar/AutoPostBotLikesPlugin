package com.github.insanusmokrassar.AutoPostBotLikesPlugin

class LikePluginConfig(
    val likeText: String = "",
    val dislikeText: String? = null,
    val likeAnswer: String = "Liked",
    val dislikeAnswer: String? = null,
    val separateAlways: Boolean = false,
    val separatedText: String = "Like? :)",
    val updatesDelay: Long = 1000
)