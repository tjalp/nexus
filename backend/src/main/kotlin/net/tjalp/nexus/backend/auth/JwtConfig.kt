package net.tjalp.nexus.backend.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import net.tjalp.nexus.auth.User
import java.util.*

/**
 * Configuration for JWT token generation and validation.
 */
object JwtConfig {
    private val secret = System.getenv("JWT_SECRET") ?: "default-secret-change-in-production"
    private val issuer = System.getenv("JWT_ISSUER") ?: "nexus-backend"
    private val audience = System.getenv("JWT_AUDIENCE") ?: "nexus-client"
    private val realm = "Nexus API"

    // Token expiration times
    const val ACCESS_TOKEN_EXPIRATION = 15 * 60 * 1000L // 15 minutes
    const val REFRESH_TOKEN_EXPIRATION = 7 * 24 * 60 * 60 * 1000L // 7 days

    val algorithm: Algorithm = Algorithm.HMAC256(secret)

    /**
     * Generates an access token for the given user.
     *
     * @param user The user to generate the token for.
     * @return The generated JWT token string.
     */
    fun generateAccessToken(user: User): String {
        return JWT.create()
            .withAudience(audience)
            .withIssuer(issuer)
            .withClaim("userId", user.id.toString())
            .withClaim("username", user.username)
            .withClaim("role", user.role.name)
            .withClaim("profileId", user.profileId?.toString())
            .withExpiresAt(Date(System.currentTimeMillis() + ACCESS_TOKEN_EXPIRATION))
            .sign(algorithm)
    }

    /**
     * Generates a refresh token for the given user.
     *
     * @param user The user to generate the token for.
     * @return The generated JWT refresh token string.
     */
    fun generateRefreshToken(user: User): String {
        return JWT.create()
            .withAudience(audience)
            .withIssuer(issuer)
            .withClaim("userId", user.id.toString())
            .withClaim("type", "refresh")
            .withExpiresAt(Date(System.currentTimeMillis() + REFRESH_TOKEN_EXPIRATION))
            .sign(algorithm)
    }

    fun getIssuer() = issuer
    fun getAudience() = audience
    fun getRealm() = realm
}

