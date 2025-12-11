package net.tjalp.nexus.util

import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import net.tjalp.nexus.scheduler.Scheduler
import net.tjalp.nexus.scheduler.ticks
import org.spongepowered.configurate.reactive.Disposable
import kotlin.time.Duration
import kotlin.time.TimeSource

/**
 * A dynamic countdown timer bound to a Scheduler.
 * - Uses wall-clock time for accuracy.
 * - Supports adding arbitrary time while running.
 * - Calls onFinished when the remaining time reaches zero.
 */
class CountdownTimer(
    private val scheduler: Scheduler,
    val initialDuration: Duration,
    private val onTick: (remaining: Duration) -> Unit = {},
    private val onFinished: () -> Unit
) : Disposable {

    /**
     * The remaining duration of the countdown timer.
     */
    var remaining: Duration = initialDuration; private set

    private var anchor = TimeSource.Monotonic.markNow()
    private var job: Job? = null

    /**
     * Starts the countdown timer.
     */
    fun start() {
        if (job != null) return
        // Re-anchor so elapsed is measured from now
        anchor = TimeSource.Monotonic.markNow()
        job = scheduler.launch {
            while (isActive) {
                // Compute elapsed since last anchor and subtract
                val elapsed = anchor.elapsedNow()
                remaining -= elapsed
                anchor = TimeSource.Monotonic.markNow()

                if (remaining <= Duration.ZERO) {
                    onTick(Duration.ZERO)
                    onFinished()
                    break
                } else {
                    onTick(remaining)
                }

                // Sleep a short interval; keep it small for responsiveness
                delay(1.ticks)
            }
        }
    }

    /**
     * Adds time to the countdown timer.
     *
     * @param delta The duration to add
     */
    fun addTime(delta: Duration) {
        // Maintain wall-clock accuracy by adding to remaining and re-anchoring
        remaining += delta
        anchor = TimeSource.Monotonic.markNow()
    }

    /**
     * Sets the remaining time of the countdown timer.
     */
    fun setRemaining(newRemaining: Duration) {
        remaining = newRemaining
        anchor = TimeSource.Monotonic.markNow()
    }

    /**
     * Whether the countdown timer is currently running.
     */
    val isRunning: Boolean get() = job?.isActive == true

    override fun dispose() {
        job?.cancel()
        job = null
    }
}