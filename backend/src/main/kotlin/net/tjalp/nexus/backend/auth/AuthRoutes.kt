package net.tjalp.nexus.backend.auth

import com.auth0.jwt.JWT
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import net.tjalp.nexus.auth.AuthService
import net.tjalp.nexus.auth.Role
import java.util.*

/**
 * Request body for login endpoint.
 */
@Serializable
data class LoginRequest(
    val username: String,
    val password: String
)

/**
 * Request body for user registration endpoint.
 */
@Serializable
data class RegisterRequest(
    val username: String,
    val password: String,
    val profileId: String
)

/**
 * Response containing authentication tokens.
 */
@Serializable
data class AuthResponse(
    val accessToken: String,
    val refreshToken: String,
    val userId: String,
    val username: String,
    val role: String
)

/**
 * Request body for token refresh endpoint.
 */
@Serializable
data class RefreshTokenRequest(
    val refreshToken: String
)

/**
 * Configures authentication routes.
 */
fun Route.authRoutes(authService: AuthService) {
    route("/auth") {
        /**
         * Login endpoint.
         *
         * POST /auth/login
         * Body: { "username": "user", "password": "pass" }
         *
         * Responses:
         *  - 200 OK: { "accessToken": "...", "refreshToken": "...", "userId": "...", "username": "...", "role": "..." }
         *  - 400 Bad Request: Invalid request body
         *  - 401 Unauthorized: Invalid credentials
         */
        post("/login") {
            val request = try {
                call.receive<LoginRequest>()
            } catch (e: Exception) {
                return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid request body"))
            }

            val user = authService.authenticate(request.username, request.password)
                ?: return@post call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid credentials"))

            val accessToken = JwtConfig.generateAccessToken(user)
            val refreshToken = JwtConfig.generateRefreshToken(user)

            call.respond(
                AuthResponse(
                    accessToken = accessToken,
                    refreshToken = refreshToken,
                    userId = user.id.toString(),
                    username = user.username,
                    role = user.role.name
                )
            )
        }

        /**
         * Register endpoint.
         *
         * POST /auth/register
         * Body: { "username": "user", "password": "pass", "profileId": "uuid" }
         *
         * Responses:
         *  - 201 Created: { "accessToken": "...", "refreshToken": "...", "userId": "...", "username": "...", "role": "..." }
         *  - 400 Bad Request: Invalid request body or profile ID
         *  - 409 Conflict: Username already exists
         */
        post("/register") {
            val request = try {
                call.receive<RegisterRequest>()
            } catch (e: Exception) {
                return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid request body"))
            }

            val profileId = try {
                UUID.fromString(request.profileId)
            } catch (e: IllegalArgumentException) {
                return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid profile ID"))
            }

            // Check if username already exists
            val existingUser = authService.getUserByUsername(request.username)
            if (existingUser != null) {
                return@post call.respond(HttpStatusCode.Conflict, mapOf("error" to "Username already exists"))
            }

            val user = try {
                authService.createUser(profileId, request.username, request.password, Role.PLAYER)
            } catch (e: Exception) {
                return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Failed to create user: ${e.message}"))
            }

            val accessToken = JwtConfig.generateAccessToken(user)
            val refreshToken = JwtConfig.generateRefreshToken(user)

            call.respond(
                HttpStatusCode.Created,
                AuthResponse(
                    accessToken = accessToken,
                    refreshToken = refreshToken,
                    userId = user.id.toString(),
                    username = user.username,
                    role = user.role.name
                )
            )
        }

        /**
         * Refresh token endpoint.
         *
         * POST /auth/refresh
         * Body: { "refreshToken": "..." }
         *
         * Responses:
         *  - 200 OK: { "accessToken": "...", "refreshToken": "...", "userId": "...", "username": "...", "role": "..." }
         *  - 400 Bad Request: Invalid request body
         *  - 401 Unauthorized: Invalid or expired refresh token
         */
        post("/refresh") {
            val request = try {
                call.receive<RefreshTokenRequest>()
            } catch (e: Exception) {
                return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid request body"))
            }

            // Verify the refresh token
            val verifier = JWT
                .require(JwtConfig.algorithm)
                .withAudience(JwtConfig.getAudience())
                .withIssuer(JwtConfig.getIssuer())
                .build()

            val decodedJWT = try {
                verifier.verify(request.refreshToken)
            } catch (e: Exception) {
                return@post call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid or expired refresh token"))
            }

            // Verify it's a refresh token
            val tokenType = decodedJWT.getClaim("type").asString()
            if (tokenType != "refresh") {
                return@post call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid token type"))
            }

            // Get the user from the database
            val userId = decodedJWT.getClaim("userId").asInt()
                ?: return@post call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid token payload"))

            val user = authService.getUserById(userId)
                ?: return@post call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "User not found"))

            // Generate new tokens
            val accessToken = JwtConfig.generateAccessToken(user)
            val refreshToken = JwtConfig.generateRefreshToken(user)

            call.respond(
                AuthResponse(
                    accessToken = accessToken,
                    refreshToken = refreshToken,
                    userId = user.id.toString(),
                    username = user.username,
                    role = user.role.name
                )
            )
        }
    }
}

