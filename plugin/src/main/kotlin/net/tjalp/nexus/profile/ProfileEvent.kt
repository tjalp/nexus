package net.tjalp.nexus.profile

import net.tjalp.nexus.profile.model.ProfileSnapshot
import net.tjalp.nexus.util.asPlayer
import org.bukkit.entity.Player
import java.util.*

/**
 * Sealed interface representing events related to profile updates.
 */
sealed interface ProfileEvent {

    /**
     * Data class representing an updated profile event.
     *
     * @property id The [UUID] of the updated profile.
     * @property old The old [ProfileSnapshot] before the update, or null if it didn't exist.
     * @property new The new [ProfileSnapshot] after the update.
     */
    data class Updated(
        val id: UUID,
        val old: ProfileSnapshot?,
        val new: ProfileSnapshot
    ) : ProfileEvent {
        val player: Player?; get() = id.asPlayer()
    }
}