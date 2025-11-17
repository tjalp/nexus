package net.tjalp.nexus.common.profile

/**
 * Sealed interface representing events related to profile updates.
 */
sealed interface ProfileEvent {

    /**
     * Data class representing an updated profile event.
     *
     * @property id The [ProfileId] of the updated profile.
     * @property old The old [ProfileSnapshot] before the update, or null if it didn't exist.
     * @property new The new [ProfileSnapshot] after the update.
     */
    data class Updated(
        val id: ProfileId,
        val old: ProfileSnapshot?,
        val new: ProfileSnapshot
    ) : ProfileEvent
}