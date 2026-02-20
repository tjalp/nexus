package net.tjalp.nexus.feature.games

import net.kyori.adventure.text.Component

sealed class JoinResult {
    /**
     * Indicates that the entity successfully joined the game, along with its tracked participant state
     * that should be used for per-game status and team assignments.
     */
    data class Success(val participant: GameParticipant) : JoinResult()

    data class Failure(
        val reason: JoinFailureReason,
        val message: Component? = null
    ) : JoinResult()
}

enum class JoinFailureReason {
    ALREADY_IN_GAME,
    GAME_FULL,
    GAME_FINISHED,
    PHASE_DISALLOWS_JOIN,
    REQUIREMENTS_NOT_MET
}
