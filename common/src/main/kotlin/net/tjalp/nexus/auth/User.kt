package net.tjalp.nexus.auth

import kotlinx.serialization.Serializable
import net.tjalp.nexus.serializer.UUIDAsStringSerializer
import java.util.*

/**
 * Represents an authenticated user in the system.
 *
 * @param id The unique identifier of the user.
 * @param profileId The UUID of the linked Minecraft profile (optional, can be null if profile is unlinked).
 * @param username The username of the user.
 * @param role The role of the user in the system.
 */
@Serializable
data class User(
    val id: Int,
    @Serializable(with = UUIDAsStringSerializer::class)
    val profileId: UUID?,
    val username: String,
    val role: Role
) {
    /**
     * Checks if the user has moderator or administrator privileges.
     */
    fun isModerator(): Boolean = role == Role.MODERATOR || role == Role.ADMIN

    /**
     * Checks if the user has administrator privileges.
     */
    fun isAdmin(): Boolean = role == Role.ADMIN

    /**
     * Checks if the user can view punishments for the given profile.
     *
     * @param targetProfileId The UUID of the profile to check.
     * @return true if the user can view the punishments, false otherwise.
     */
    fun canViewPunishments(targetProfileId: UUID): Boolean {
        return profileId == targetProfileId || isModerator()
    }

    /**
     * Checks if the user can modify a profile.
     *
     * @param targetProfileId The UUID of the profile to check.
     * @return true if the user can modify the profile, false otherwise.
     */
    fun canModifyProfile(targetProfileId: UUID): Boolean {
        return profileId == targetProfileId || isModerator()
    }

    /**
     * Checks if the user has a linked profile.
     */
    fun hasLinkedProfile(): Boolean = profileId != null
}

