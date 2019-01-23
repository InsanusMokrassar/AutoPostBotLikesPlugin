package com.github.insanusmokrassar.AutoPostBotLikesPlugin.utils.extensions

import com.github.insanusmokrassar.AutoPostTelegramBot.utils.NewDefaultCoroutineScope
import com.github.insanusmokrassar.AutoPostTelegramBot.utils.extensions.subscribe
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.channels.Channel
import java.util.concurrent.TimeUnit

fun <T> BroadcastChannel<T>.debounceByValue(
    delayTime: Long,
    timeUnit: TimeUnit = TimeUnit.MILLISECONDS,
    scope: CoroutineScope = NewDefaultCoroutineScope(2)
): BroadcastChannel<T> {
    val outBroadcastChannel = BroadcastChannel<T>(Channel.CONFLATED)
    val values = HashMap<T, Job>()

    val channel = Channel<T>(Channel.CONFLATED)
    scope.launch {
        for (msg in channel) {
            values[msg] ?.cancel()
            values[msg] = launch {
                delay(timeUnit.toMillis(delayTime))

                outBroadcastChannel.send(msg)
            }
        }
    }

    subscribe {
        channel.send(it)
    }

    return outBroadcastChannel
}
