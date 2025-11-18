package net.tjalp.nexus.profile

import net.tjalp.nexus.profile.model.ProfileEntity
import java.util.*

/**
 * Sealed interface representing events related to profile updates.
 */
sealed interface ProfileEvent {

    /**
     * Data class representing an updated profile event.
     *
     * @property id The [UUID] of the updated profile.
     * @property old The old [ProfileEntity] before the update, or null if it didn't exist.
     * @property new The new [ProfileEntity] after the update.
     */
    data class Updated(
        val id: UUID,
        val old: ProfileEntity?,
        val new: ProfileEntity
    ) : ProfileEvent
}