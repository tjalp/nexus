package net.tjalp.nexus.common

import org.bukkit.plugin.java.JavaPlugin

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
     *
     * @param plugin The JavaPlugin instance to enable the feature in.
     */
    fun enable(plugin: JavaPlugin)

    /**
     * Disables the feature within the given plugin.
     */
    fun disable()
}