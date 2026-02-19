package net.tjalp.nexus.auth

import java.util.*

/**
 * Service for user authentication and authorization.
 */
interface AuthService {
    /**
     * Authenticates a user with the given username and password.
     *
     * @param username The username of the user.
     * @param password The password of the user.
     * @return The authenticated user, or null if authentication fails.
     */
    suspend fun authenticate(username: String, password: String): User?

    /**
     * Retrieves a user by their ID.
     *
     * @param userId The ID of the user.
     * @return The user, or null if not found.
     */
    suspend fun getUserById(userId: Int): User?

    /**
     * Retrieves a user by their username.
     *
     * @param username The username of the user.
     * @return The user, or null if not found.
     */
    suspend fun getUserByUsername(username: String): User?

    /**
     * Retrieves a user by their linked profile ID.
     *
     * @param profileId The UUID of the profile.
     * @return The user, or null if not found.
     */
    suspend fun getUserByProfileId(profileId: UUID): User?

    /**
     * Creates a new user with the given details.
     *
     * @param profileId The profile UUID to associate with the user (optional).
     * @param username The username of the user.
     * @param password The password of the user.
     * @param role The role of the user.
     * @return The created user.
     */
    suspend fun createUser(profileId: UUID?, username: String, password: String, role: Role = Role.PLAYER): User

    /**
     * Updates the role of a user.
     *
     * @param userId The ID of the user.
     * @param role The new role of the user.
     * @return true if the update was successful, false otherwise.
     */
    suspend fun updateUserRole(userId: Int, role: Role): Boolean

    /**
     * Links a profile to a user. If the profile is already linked to another user,
     * that user's profile will be unlinked first.
     *
     * @param userId The ID of the user to link the profile to.
     * @param profileId The UUID of the profile to link.
     * @return The updated user, or null if the user was not found.
     */
    suspend fun linkProfile(userId: Int, profileId: UUID): User?

    /**
     * Unlinks a profile from a user.
     *
     * @param userId The ID of the user to unlink the profile from.
     * @return The updated user, or null if the user was not found.
     */
    suspend fun unlinkProfile(userId: Int): User?

    /**
     * Synchronizes the role of a user with their Minecraft profile.
     * This is useful for keeping web and in-game permissions in sync.
     *
     * @param profileId The UUID of the profile.
     * @param role The role to synchronize.
     * @return true if the synchronization was successful, false otherwise.
     */
    suspend fun syncRoleWithProfile(profileId: UUID, role: Role): Boolean
}

