package net.tjalp.nexus.auth

import net.tjalp.nexus.profile.model.ProfilesTable
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.datetime.CurrentTimestamp
import org.jetbrains.exposed.v1.datetime.timestamp
import kotlin.time.ExperimentalTime

/**
 * Database table for storing user authentication credentials.
 *
 * Users can optionally be linked to a Minecraft profile. When a profile is linked to a new user,
 * any existing user with that profile will have their profileId set to null.
 */
@OptIn(ExperimentalTime::class)
object UsersTable : IntIdTable("users") {
    val profileId = reference("profile_id", ProfilesTable.id, onDelete = ReferenceOption.SET_NULL).nullable().index()
    val username = varchar("username", 255).uniqueIndex()
    val passwordHash = varchar("password_hash", 255)
    val role = varchar("role", 50).default("PLAYER")
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp)
}

