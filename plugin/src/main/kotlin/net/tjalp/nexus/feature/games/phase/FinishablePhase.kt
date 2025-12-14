package net.tjalp.nexus.feature.games.phase

/**
 * Indicates that a game phase can be finished.
 */
interface FinishablePhase {

    /**
     * Finishes the game phase, performing any necessary cleanup or transitions.
     */
    suspend fun finish()
}