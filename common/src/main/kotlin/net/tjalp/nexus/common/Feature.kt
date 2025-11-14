package net.tjalp.nexus.common

import net.tjalp.nexus.common.profile.ProfileModule

/**
 * Represents a feature that can be enabled or disabled within the Nexus plugin.
 */
interface Feature {

    /**
     * The name of the feature. Should be lowercase and unique across all features.
     */
    val name: String

    /**
     * The [ProfileModule]s provided by this feature. Empty by default
     *
     * @return A collection of [ProfileModule]s.
     */
    val profileModules: Collection<ProfileModule>
        get() = emptyList()

    /**
     * Enables the feature within the given plugin.
     */
    fun enable()

    /**
     * Disables the feature within the given plugin.
     */
    fun disable()
}