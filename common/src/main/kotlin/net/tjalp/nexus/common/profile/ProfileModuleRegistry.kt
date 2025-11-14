package net.tjalp.nexus.common.profile

class ProfileModuleRegistry(
    private val modules: Collection<ProfileModule>
) {

    suspend fun initializeProfileModules(profile: ProfileSnapshot) {
        modules.forEach { it.onProfileLoad(profile) }
    }

    suspend fun saveProfileModules(profile: ProfileSnapshot) {
        modules.forEach { it.onProfileSave(profile) }
    }
}