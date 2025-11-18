package net.tjalp.nexus

/**
 * Represents a feature that can be enabled or disabled within the Nexus plugin.
 */
interface Feature {

    /**
     * The name of the feature. Should be lowercase and unique across all features.
     */
    val name: String

    /**
     * Enables the feature within the given plugin.
     */
    fun enable()

    /**
     * Disables the feature within the given plugin.
     */
    fun disable()
}