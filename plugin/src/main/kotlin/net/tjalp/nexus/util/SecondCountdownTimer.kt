package net.tjalp.nexus.util

import net.tjalp.nexus.scheduler.Scheduler
import org.bukkit.scheduler.BukkitTask

/**
 * A simple second-based countdown timer bound to a Scheduler.
 * - Calls onFinished when the remaining time reaches zero.
 */
class SecondCountdownTimer(
    private val scheduler: Scheduler,
    val initialTime: Long,
    private val onTick: (remaining: Long) -> Unit = {},
    private val onFinished: () -> Unit
) {

    private var _remaining: Long = initialTime
    /**
     * The remaining time in seconds.
     */
    var remaining: Long = _remaining
        get() = _remaining
        set(value) {
            _remaining = value
            if (field <= 0) {
                pause()
                onFinished()
            } else onTick(value)
        }

    private var task: BukkitTask? = null

    /**
     * Starts the countdown timer.
     *
     * Throws IllegalStateException if the timer is already running.
     */
    fun start() {
        require(task == null) { "Tried to start timer while already running" }

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
     * Resets the timer to the initial time.
     */
    fun reset() {
        _remaining = initialTime
    }

    /**
     * Pauses the timer if it is running.
     */
    fun pause() {
        task?.cancel()
        task = null
    }

    /**
     * Checks if the timer is currently running.
     */
    val isRunning: Boolean
        get() = task != null
}