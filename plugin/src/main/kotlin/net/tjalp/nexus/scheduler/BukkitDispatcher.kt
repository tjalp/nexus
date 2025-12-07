package net.tjalp.nexus.scheduler

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Runnable
import net.tjalp.nexus.NexusPlugin
import org.bukkit.Bukkit
import kotlin.coroutines.CoroutineContext

object BukkitDispatcher : CoroutineDispatcher() {

    override fun isDispatchNeeded(context: CoroutineContext): Boolean = !Bukkit.isPrimaryThread()

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        Bukkit.getScheduler().runTask(NexusPlugin, block)
    }
}

/**
 * Converts an integer representing Minecraft ticks to milliseconds.
 * 1 second = 20 ticks, but since the scheduler works with the BukkitScheduler,
 * we subtract 25 milliseconds to account for scheduling delay.
 * Therefore, 1 tick = 50 milliseconds ONLY when using delay()
 */
val Int.ticks: Long; get() = this * 50L - 25