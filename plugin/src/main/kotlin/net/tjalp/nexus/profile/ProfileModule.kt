package net.tjalp.nexus.profile

import net.tjalp.nexus.profile.model.ProfileEntity

/**
 * Interface for modules that handle profile load and save events.
 */
interface ProfileModule {

    /**
     * Called when a profile is loaded.
     *
     * @param profile The loaded [ProfileEntity].
     */
    suspend fun onProfileLoad(profile: ProfileEntity)

    /**
     * Called when a profile is saved.
     *
     * @param profile The saved [ProfileEntity].
     */
    suspend fun onProfileSave(profile: ProfileEntity)
}