package net.tjalp.nexus.common.profile

/**
 * Interface for modules that handle profile load and save events.
 */
interface ProfileModule {

    /**
     * Called when a profile is loaded.
     *
     * @param profile The loaded [ProfileSnapshot].
     */
    suspend fun onProfileLoad(profile: ProfileSnapshot)

    /**
     * Called when a profile is saved.
     *
     * @param profile The saved [ProfileSnapshot].
     */
    suspend fun onProfileSave(profile: ProfileSnapshot)
}