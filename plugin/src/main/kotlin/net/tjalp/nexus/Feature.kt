package net.tjalp.nexus

import kotlinx.coroutines.CoroutineScope

/**
 * Represents a feature that can be enabled or disabled within the Nexus plugin.
 */
interface Feature {

    /**
     * The name of the feature. Should be lowercase and unique across all features.
     */
    val name: String

    /**
     * Indicates whether the feature is currently enabled.
     */
    val isEnabled: Boolean

    /**
     * The scheduler to be used for coroutine operations within the feature.
     */
    val scheduler: CoroutineScope
        get() = error("Feature scheduler not initialized")

    /**
     * Enables the feature within the given plugin.
     */
    fun enable()

    /**
     * Disables the feature within the given plugin.
     */
    fun disable()
}