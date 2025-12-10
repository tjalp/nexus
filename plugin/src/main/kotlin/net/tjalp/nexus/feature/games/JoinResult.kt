package net.tjalp.nexus.feature.games

/**
 * Reasons for failure when attempting to join a game.
 */
enum class JoinFailureReason {
    OUT_OF_BOUNDS,
    WRONG_PHASE,
    GAME_FULL,
    ALREADY_JOINED,
    UNKNOWN
}

/**
 * Result of an attempt to join a game.
 */
sealed class JoinResult {
    data object Success : JoinResult()
    data class Failure(val reason: JoinFailureReason, val message: String? = null) : JoinResult()
}