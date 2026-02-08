package net.tjalp.nexus

import net.tjalp.nexus.scheduler.Scheduler
import org.spongepowered.configurate.reactive.Disposable

/**
 * Represents a feature that can be enabled or disabled within the Nexus plugin.
 *
 * @param key The unique name of the feature.
 */
abstract class Feature(
    val id: String
) : Disposable {

    /**
     * Indicates whether the feature is currently enabled.
     */
    var isEnabled: Boolean = false; private set

    /**
     * The scheduler to be used for coroutine operations within the feature.
     */
    lateinit var scheduler: Scheduler; private set

    /**
     * Enables the feature within the given plugin.
     */
    fun enable() {
        isEnabled = true
        scheduler = NexusPlugin.scheduler.fork("feature/$id")

        this.onEnable()
    }

    /**
     * Called when the feature is enabled, allowing for setup of resources.
     */
    open fun onEnable() {}

    /**
     * Called when the feature is disposed, allowing for cleanup of resources.
     */
    open fun onDisposed() {}

    override fun dispose() {
        this.onDisposed()

        scheduler.dispose()
        isEnabled = false
    }
}