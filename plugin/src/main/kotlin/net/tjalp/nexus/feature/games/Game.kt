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
            throw RuntimeException("Failed to load game phase ${phase::class.simpleName} for game $id of type ${type.name}", e)
        }
    }

    suspend fun enterNextPhase() {
        enterPhase(nextPhase)
    }

    suspend fun join(player: Player): Boolean {
        val success = currentPhase?.onJoin(player) ?: false

        if (success) {
            _participants.add(player.uniqueId)
        }

        return success
    }

    fun leave(player: Player) {
        currentPhase?.onLeave(player)

        _participants.remove(player.uniqueId)
    }

    override fun dispose() {
        scheduler.dispose()
    }
}