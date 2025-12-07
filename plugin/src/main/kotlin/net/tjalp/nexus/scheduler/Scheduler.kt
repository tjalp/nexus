package net.tjalp.nexus.scheduler

import kotlinx.coroutines.*
import net.tjalp.nexus.NexusPlugin
import org.bukkit.Bukkit
import org.bukkit.scheduler.BukkitTask
import org.spongepowered.configurate.reactive.Disposable
import java.util.concurrent.Executor
import kotlin.coroutines.CoroutineContext

/**
 * A Scheduler represents a scoped coroutine context for scheduling tasks, as well as a Bukkit scheduler.
 * It can be forked to create child schedulers that inherit the parent's context.
 *
 * @property id A unique identifier for the scheduler.
 * @property parent An optional parent scheduler from which this scheduler inherits context.
 */
class Scheduler(
    val id: String,
    val parent: Scheduler? = null
) : CoroutineScope, Executor, Disposable {

    override val coroutineContext: CoroutineContext = (parent?.coroutineContext ?: BukkitDispatcher) + SupervisorJob()

    internal val children: MutableList<Scheduler> = mutableListOf()

    private val bukkitScheduler get() = Bukkit.getScheduler()
    private val tasks = mutableListOf<BukkitTask>()

    /**
     * The full hierarchical path of the scheduler, constructed from its parents.
     */
    val path: String
        get() = parent?.let { "${it.path}/$id" } ?: id

    /**
     * Schedules a task to be executed after a specified delay in ticks.
     *
     * @param ticks The delay in ticks before executing the task.
     * @param block The suspend function to execute after the delay.
     * @return The scheduled BukkitTask.
     * @throws IllegalStateException if the scheduler is inactive.
     */
    fun delay(ticks: Long, block: suspend () -> Unit): BukkitTask {
        if (!isActive) throw IllegalStateException("Cannot schedule task on inactive scheduler '$id' (path: $path)")

        val task = bukkitScheduler.runTaskLater(NexusPlugin, Runnable {
            launch(Dispatchers.Unconfined) { block() }
        }, ticks)

        tasks += task

        return task
    }

    /**
     * Schedules a repeating task with an initial delay and a fixed interval.
     *
     * @param initialDelay The delay in ticks before the first execution of the task.
     * @param interval The interval in ticks between subsequent executions of the task.
     * @param block The suspend function to execute on each interval.
     * @return The scheduled BukkitTask.
     * @throws IllegalStateException if the scheduler is inactive.
     */
    fun repeat(initialDelay: Long = 0, interval: Long, block: suspend () -> Unit): BukkitTask {
        if (!isActive) throw IllegalStateException("Cannot schedule task on inactive scheduler '$id' (path: $path)")

        val task = bukkitScheduler.runTaskTimer(NexusPlugin, Runnable {
            launch(Dispatchers.Unconfined) { block() }
        }, initialDelay, interval)

        tasks += task

        return task
    }

    /**
     * Forks the current scheduler to create a child scheduler with the specified ID.
     *
     * @param childId The unique identifier for the child scheduler.
     * @return A new Scheduler instance that is a child of the current scheduler.
     * @throws IllegalStateException if the current scheduler is inactive.
     */
    fun fork(childId: String): Scheduler {
        if (!isActive) throw IllegalStateException("Cannot fork from inactive scheduler '$id' (path: $path)")

        return Scheduler(id = childId, parent = this).also { children += it }
    }

    override fun execute(command: Runnable) {
        launch { command.run() }
    }

    override fun dispose() {
        parent?.children?.remove(this)

        tasks.forEach { it.cancel() }
        tasks.clear()

        children.iterator().forEachRemaining { it.dispose() }
        cancel("Scheduler '$id' (path: $path) disposed")
    }
}