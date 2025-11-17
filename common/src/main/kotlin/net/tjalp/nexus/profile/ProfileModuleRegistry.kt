package net.tjalp.nexus.profile

/**
 * Registry for all profile modules to handle profile load and save events.
 *
 * @param modules The collection of [ProfileModule]s to be managed.
 */
class ProfileModuleRegistry(
    private val modules: Collection<ProfileModule>
) {

    /**
     * Initializes all profile modules for the given profile.
     *
     * @param profile The [ProfileSnapshot] to initialize modules for.
     */
    suspend fun initializeProfileModules(profile: ProfileSnapshot) {
        modules.forEach { it.onProfileLoad(profile) }
    }

    /**
     * Saves all profile modules for the given profile.
     *
     * @param profile The [ProfileSnapshot] to save modules for.
     */
    suspend fun saveProfileModules(profile: ProfileSnapshot) {
        modules.forEach { it.onProfileSave(profile) }
    }
}