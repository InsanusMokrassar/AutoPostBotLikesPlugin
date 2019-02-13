package com.github.insanusmokrassar.AutoPostBotLikesPlugin.utils.extensions

import com.github.insanusmokrassar.AutoPostTelegramBot.extraSmallBroadcastCapacity
import com.github.insanusmokrassar.AutoPostTelegramBot.utils.NewDefaultCoroutineScope
import com.github.insanusmokrassar.AutoPostTelegramBot.utils.extensions.subscribe
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.channels.Channel
import java.util.concurrent.TimeUnit

private sealed class DebounceAction<T>

private data class AddValue<T>(val value: T) : DebounceAction<T>()
private data class RemoveJob<T>(val value: T, val job: Job) : DebounceAction<T>()

fun <T> BroadcastChannel<T>.debounceByValue(
    delayTime: Long,
    timeUnit: TimeUnit = TimeUnit.MILLISECONDS,
    scope: CoroutineScope = NewDefaultCoroutineScope(2),
    resultBroadcastChannelCapacity: Int = extraSmallBroadcastCapacity
): BroadcastChannel<T> {
    val outBroadcastChannel = BroadcastChannel<T>(resultBroadcastChannelCapacity)
    val values = HashMap<T, Job>()
    val delayMillis = timeUnit.toMillis(delayTime)

    val channel = Channel<DebounceAction<T>>(extraSmallBroadcastCapacity)
    scope.launch {
        for (action in channel) {
            when (action) {
                is AddValue -> {
                    val msg = action.value
                    values[msg] ?.cancel()
                    lateinit var job: Job
                    job = launch {
                        delay(delayMillis)

                        outBroadcastChannel.send(msg)
                        channel.send(RemoveJob(msg, job))
                    }
                    values[msg] = job
                }
                is RemoveJob -> if (values[action.value] == action.job) {
                    values.remove(action.value)
                }
            }
            
        }
    }

    subscribe {
        channel.send(AddValue(it))
    }

    return outBroadcastChannel
}
