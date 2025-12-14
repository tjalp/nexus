package net.tjalp.nexus.util

import net.tjalp.nexus.NexusPlugin
import net.tjalp.nexus.scheduler.Scheduler
import org.bukkit.scheduler.BukkitTask
import org.spongepowered.configurate.reactive.Disposable

/**
 * A simple second-based countdown timer bound to a Scheduler.
 * - Calls onFinished when the remaining time reaches zero.
 */
class SecondCountdownTimer(
    private val scheduler: Scheduler,
    val initialTime: Long,
    private val onTick: (remaining: Long) -> Unit = {},
    private val onFinished: () -> Unit
) : Disposable {

    private var _remaining: Long = initialTime
    /**
     * The remaining time in seconds.
     */
    var remaining: Long = _remaining
        set(value) {
            _remaining = value
            if (field <= 0) {
                onFinished()
            } else onTick(value)
        }

    private var task: BukkitTask? = null

    /**
     * Starts the countdown timer.
     */
    fun start() {
        task = scheduler.repeat(initialDelay = 20, interval = 20) {
            _remaining--
            if (_remaining <= 0) {
                onFinished()
            } else {
                onTick(_remaining)
            }
        }
    }

    /**
     * Checks if the timer is currently running.
     */
    val isRunning: Boolean
        get() = task?.taskId?.let { NexusPlugin.server.scheduler.isCurrentlyRunning(it) } ?: false

    override fun dispose() {
        task?.cancel()
        task = null
    }
}