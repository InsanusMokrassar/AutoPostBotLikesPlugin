package com.github.insanusmokrassar.AutoPostBotLikesPlugin.utils.extensions

import com.github.insanusmokrassar.AutoPostTelegramBot.utils.extensions.subscribe
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.channels.*
import java.util.concurrent.TimeUnit

fun <T> BroadcastChannel<T>.debounceByValue(delayTime: Long, timeUnit: TimeUnit = TimeUnit.MILLISECONDS): BroadcastChannel<T> {
    val outBroadcastChannel = BroadcastChannel<T>(Channel.CONFLATED)
    val values = HashMap<T, Job>()

    val actor = actor<T> {
        for (msg in channel) {
            values[msg] ?.cancel()
            values[msg] = launch {
                delay(delayTime, timeUnit)

                outBroadcastChannel.send(msg)
            }
        }
    }

    subscribe {
        actor.send(it)
    }

    return outBroadcastChannel
}
