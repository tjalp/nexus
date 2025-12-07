package net.tjalp.nexus.feature

import net.tjalp.nexus.NexusPlugin
import net.tjalp.nexus.scheduler.Scheduler

/**
 * Represents a feature that can be enabled or disabled within the Nexus plugin.
 *
 * @param name The unique name of the feature.
 */
abstract class Feature(
    val name: String
) {

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
    open fun enable() {
        isEnabled = true

        scheduler = NexusPlugin.scheduler.fork("feature/$name")
    }

    /**
     * Disables the feature within the given plugin.
     */
    open fun disable() {
        scheduler.dispose()

        isEnabled = false
    }
}