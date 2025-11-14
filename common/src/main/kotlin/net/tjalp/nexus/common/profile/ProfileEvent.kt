package net.tjalp.nexus.common.profile

sealed interface ProfileEvent {
    data class Updated(
        val id: ProfileId,
        val old: ProfileSnapshot?,
        val new: ProfileSnapshot
    ) : ProfileEvent
}