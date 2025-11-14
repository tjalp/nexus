package net.tjalp.nexus.common.profile

interface ProfileModule {

    suspend fun onProfileLoad(profile: ProfileSnapshot)

    suspend fun onProfileSave(profile: ProfileSnapshot)
}