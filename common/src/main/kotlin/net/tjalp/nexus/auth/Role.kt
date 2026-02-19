package net.tjalp.nexus.auth

import kotlinx.serialization.Serializable

/**
 * Represents the role of a user in the system.
 */
@Serializable
enum class Role {
    /**
     * A regular player with standard permissions.
     */
    PLAYER,

    /**
     * A moderator who can view all punishments and perform moderation actions.
     */
    MODERATOR,

    /**
     * An administrator with full access to all system features.
     */
    ADMIN
}

