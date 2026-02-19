package net.tjalp.nexus.auth.service

import net.tjalp.nexus.auth.AuthService
import net.tjalp.nexus.auth.Role
import net.tjalp.nexus.auth.User
import net.tjalp.nexus.auth.UsersTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.update
import org.mindrot.jbcrypt.BCrypt
import java.util.*

/**
 * Implementation of [AuthService] using Exposed database.
 */
class ExposedAuthService(private val database: Database) : AuthService {

    override suspend fun authenticate(username: String, password: String): User? = suspendTransaction(database) {
        val row = UsersTable
            .selectAll()
            .where { UsersTable.username eq username }
            .singleOrNull() ?: return@suspendTransaction null

        val passwordHash = row[UsersTable.passwordHash]
        if (!BCrypt.checkpw(password, passwordHash)) {
            return@suspendTransaction null
        }

        User(
            id = row[UsersTable.id].value,
            profileId = row[UsersTable.profileId]?.value,
            username = row[UsersTable.username],
            role = Role.valueOf(row[UsersTable.role])
        )
    }

    override suspend fun getUserById(userId: Int): User? = suspendTransaction(database) {
        UsersTable
            .selectAll()
            .where { UsersTable.id eq userId }
            .singleOrNull()
            ?.let {
                User(
                    id = it[UsersTable.id].value,
                    profileId = it[UsersTable.profileId]?.value,
                    username = it[UsersTable.username],
                    role = Role.valueOf(it[UsersTable.role])
                )
            }
    }

    override suspend fun getUserByUsername(username: String): User? = suspendTransaction(database) {
        UsersTable
            .selectAll()
            .where { UsersTable.username eq username }
            .singleOrNull()
            ?.let {
                User(
                    id = it[UsersTable.id].value,
                    profileId = it[UsersTable.profileId]?.value,
                    username = it[UsersTable.username],
                    role = Role.valueOf(it[UsersTable.role])
                )
            }
    }

    override suspend fun getUserByProfileId(profileId: UUID): User? = suspendTransaction(database) {
        UsersTable
            .selectAll()
            .where { UsersTable.profileId eq profileId }
            .singleOrNull()
            ?.let {
                User(
                    id = it[UsersTable.id].value,
                    profileId = it[UsersTable.profileId]?.value,
                    username = it[UsersTable.username],
                    role = Role.valueOf(it[UsersTable.role])
                )
            }
    }

    override suspend fun createUser(profileId: UUID?, username: String, password: String, role: Role): User =
        suspendTransaction(database) {
            val passwordHash = BCrypt.hashpw(password, BCrypt.gensalt())

            // If profileId is provided, unlink it from any existing user
            profileId?.let { unlinkProfileFromOtherUsers(it) }

            val id = UsersTable.insert {
                it[UsersTable.profileId] = profileId
                it[UsersTable.username] = username
                it[UsersTable.passwordHash] = passwordHash
                it[UsersTable.role] = role.name
            }[UsersTable.id].value

            User(
                id = id,
                profileId = profileId,
                username = username,
                role = role
            )
        }

    override suspend fun updateUserRole(userId: Int, role: Role): Boolean = suspendTransaction(database) {
        UsersTable.update({ UsersTable.id eq userId }) {
            it[UsersTable.role] = role.name
        } > 0
    }

    override suspend fun linkProfile(userId: Int, profileId: UUID): User? = suspendTransaction(database) {
        // Unlink the profile from any other user first
        unlinkProfileFromOtherUsers(profileId)

        // Link the profile to the specified user
        val updated = UsersTable.update({ UsersTable.id eq userId }) {
            it[UsersTable.profileId] = profileId
        } > 0

        if (updated) {
            getUserById(userId)
        } else {
            null
        }
    }

    override suspend fun unlinkProfile(userId: Int): User? = suspendTransaction(database) {
        val updated = UsersTable.update({ UsersTable.id eq userId }) {
            it[UsersTable.profileId] = null
        } > 0

        if (updated) {
            getUserById(userId)
        } else {
            null
        }
    }

    override suspend fun syncRoleWithProfile(profileId: UUID, role: Role): Boolean = suspendTransaction(database) {
        UsersTable.update({ UsersTable.profileId eq profileId }) {
            it[UsersTable.role] = role.name
        } > 0
    }

    /**
     * Unlinks the given profile from any user that currently has it linked.
     * This ensures that a profile is only linked to one user at a time.
     *
     * @param profileId The UUID of the profile to unlink.
     */
    private fun unlinkProfileFromOtherUsers(profileId: UUID) {
        UsersTable.update({ UsersTable.profileId eq profileId }) {
            it[UsersTable.profileId] = null
        }
    }
}

