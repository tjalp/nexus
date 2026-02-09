package net.tjalp.nexus.feature.games

import net.kyori.adventure.text.Component

interface FinishablePhase {
    suspend fun finish()
}

interface TimerPhase {
    var remainingTicks: Long?
}

open class GamePhase(
    val id: String,
    val displayName: Component,
    val allowJoin: Boolean = false,
    val joinRequirements: List<GameJoinRequirement> = emptyList(),
    private val durationTicks: Long? = null,
    private val completionCondition: (Game) -> Boolean = { false }
) : FinishablePhase, TimerPhase {

    override var remainingTicks: Long? = durationTicks
        internal set

    private var isForceFinished = false

    open suspend fun onEnter(game: Game) {}

    open suspend fun onExit(game: Game) {}

    open suspend fun onTick(game: Game, elapsedTicks: Long) {}

    fun shouldAdvance(game: Game, elapsedTicks: Long): Boolean {
        if (isForceFinished) return true
        if (completionCondition(game)) return true
        return remainingTicks?.let { it <= 0 } ?: false
    }

    override suspend fun finish() {
        isForceFinished = true
    }

    internal fun resetState() {
        remainingTicks = durationTicks
        isForceFinished = false
    }

    internal fun updateTimer(deltaTicks: Long) {
        remainingTicks = remainingTicks?.let { (it - deltaTicks).coerceAtLeast(0) }
    }
}
