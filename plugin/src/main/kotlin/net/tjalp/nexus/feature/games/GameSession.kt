package net.tjalp.nexus.feature.games

import net.tjalp.nexus.NexusPlugin
import net.tjalp.nexus.util.asEntity
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.spongepowered.configurate.reactive.Disposable
import java.util.*

class GameSession(
    private val feature: GamesFeature,
    val id: String,
    val host: GameHost,
    // definition & settings todo
    private val lifecycle: GameLifecycle
): Disposable {

    /**
     * Scheduler dedicated to this game instance.
     */
    val scheduler = feature.scheduler.fork("game/$id")

    private val _participants = mutableSetOf<UUID>()
    val participants: Set<UUID>
        get() = _participants.toSet()

    /**
     * The scoreboard used for this game instance.
     */
    val scoreboard = NexusPlugin.server.scoreboardManager.newScoreboard

    suspend fun join(entity: Entity): JoinResult {
        val phase = lifecycle.currentState
        val result = phase?.canJoin(entity)
            ?: return JoinResult.Failure(JoinFailureReason.WRONG_PHASE, "Game is not a joinable phase")

        if (result !is JoinResult.Success) return result

        _participants += entity.uniqueId

        applyParticipantPresence(entity)
        phase.onJoin(entity)

        return JoinResult.Success
    }

    fun leave(entity: Entity) {
        val phase = lifecycle.currentState

        phase?.onLeave(entity)
        removeParticipantPresence(entity)

        _participants -= entity.uniqueId
    }

    fun applyParticipantPresence(entity: Entity) {
        (entity as? Player)?.scoreboard = scoreboard
    }

    fun removeParticipantPresence(entity: Entity) {
        (entity as? Player)?.scoreboard = NexusPlugin.server.scoreboardManager.mainScoreboard
    }

    override fun dispose() {
        _participants.forEach { it.asEntity()?.let { entity -> leave(entity) } }
        lifecycle.dispose()
        scheduler.dispose()
    }
}