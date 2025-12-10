package net.tjalp.nexus.feature.games

import net.tjalp.nexus.util.asPlayer
import org.bukkit.entity.Player
import org.spongepowered.configurate.reactive.Disposable
import java.util.*

abstract class Game(
    val id: String = List(6) {
        ('a'..'z') + ('A'..'Z') + ('0'..'9')
    }.flatten().shuffled().take(6).joinToString(""),
    val type: GameType
) : Disposable {

    val scheduler = GamesFeature.scheduler.fork("game/$id")
    var currentPhase: GamePhase? = null; private set

    private val _participants = mutableSetOf<UUID>()
    val participants: Set<Player> get() = _participants.mapNotNull { it.asPlayer() }.toSet()

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
            throw RuntimeException(
                "Failed to load game phase ${phase::class.simpleName} for game $id of type ${type.name}",
                e
            )
        }
    }

    suspend fun enterNextPhase() {
        enterPhase(nextPhase)
    }

    suspend fun join(player: Player): JoinResult {
        val result =
            currentPhase?.onJoin(player) ?: JoinResult.Failure(JoinFailureReason.WRONG_PHASE, "No active phase to join")

        if (result is JoinResult.Success) {
            _participants.add(player.uniqueId)
        }

        return result
    }

    fun leave(player: Player) {
        currentPhase?.onLeave(player)

        _participants.remove(player.uniqueId)
    }

    override fun dispose() {
        scheduler.dispose()
    }
}