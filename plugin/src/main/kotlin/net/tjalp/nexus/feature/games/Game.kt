package net.tjalp.nexus.feature.games

import org.spongepowered.configurate.reactive.Disposable

abstract class Game(
    val id: String = List(6) {
        ('a'..'z') + ('A'..'Z') + ('0'..'9')
    }.flatten().shuffled().take(6).joinToString(""),
    val type: GameType
) : Disposable {

    val scheduler = GamesFeature.scheduler.fork("game/$id")

    var currentPhase: GamePhase? = null; private set
    abstract val nextPhase: GamePhase

    suspend fun enterPhase(phase: GamePhase) {
        require(phase != currentPhase) { "Cannot load the same phase twice: ${phase::class.simpleName}" }

        val previousPhase = currentPhase

        try {
            phase.load(previousPhase)
            previousPhase?.dispose()
            currentPhase = phase
            phase.start(previousPhase)
        } catch (e: Exception) {
            throw RuntimeException("Failed to load game phase ${phase::class.simpleName} for game $id of type ${type.name}", e)
        }
    }

    suspend fun enterNextPhase() {
        enterPhase(nextPhase)
    }

    override fun dispose() {
        scheduler.dispose()
    }
}