package net.tjalp.nexus.feature.games

import net.tjalp.nexus.util.asEntity
import org.spongepowered.configurate.reactive.Disposable

class GameLifecycle(
    private val session: GameSession,
    private val initialState: () -> GameState
): Disposable {
    private var _currentState: GameState? = null
    val currentState: GameState?
        get() = _currentState

    suspend fun start() {
        transitionTo(initialState())
    }

    suspend fun transitionTo(next: GameState) {
        val previous = _currentState
        previous?.leave(next)
        previous?.dispose()

        _currentState = next
        next.enter(previous)

        session.participants
            .mapNotNull { it.asEntity() }
            .forEach { next.onJoin(it) }
    }

    override fun dispose() {
        _currentState?.dispose()
        // TODO implement more
    }
}