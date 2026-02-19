package net.tjalp.nexus.backend.schema

import com.apurebase.kgraphql.Context
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import net.tjalp.nexus.auth.AuthService
import net.tjalp.nexus.auth.Role
import net.tjalp.nexus.auth.User
import java.util.*

/**
 * Extension function to get the authenticated user from GraphQL context.
 */
suspend fun Context.getAuthenticatedUser(authService: AuthService): User? {
    val call = get<ApplicationCall>() ?: return null
    val principal = call.principal<JWTPrincipal>() ?: return null

    val userId = principal.payload.getClaim("userId").asInt() ?: return null
    val username = principal.payload.getClaim("username").asString() ?: return null
    val role = principal.payload.getClaim("role").asString() ?: return null
    val profileId = UUID.fromString(principal.payload.getClaim("profileId").asString()) ?: return null

    return User(
        id = userId,
        username = username,
        role = Role.valueOf(role),
        profileId = profileId
    )
}

/**
 * Extension function to require an authenticated user from GraphQL context.
 * Throws an exception if the user is not authenticated.
 */
suspend fun Context.requireAuthenticatedUser(authService: AuthService): User {
    return getAuthenticatedUser(authService) ?: error("Authentication required")
}

/**
 * Extension function to check if a user can modify a profile.
 */
suspend fun Context.requireProfileAccess(authService: AuthService, profileId: UUID): User {
    val user = requireAuthenticatedUser(authService)
    if (!user.canModifyProfile(profileId)) {
        error("You do not have permission to modify this profile")
    }
    return user
}

/**
 * Extension function to check if a user can view punishments for a profile.
 */
suspend fun Context.requirePunishmentViewAccess(authService: AuthService, profileId: UUID): User {
    val user = requireAuthenticatedUser(authService)
    if (!user.canViewPunishments(profileId)) {
        error("You do not have permission to view punishments for this profile")
    }
    return user
}

