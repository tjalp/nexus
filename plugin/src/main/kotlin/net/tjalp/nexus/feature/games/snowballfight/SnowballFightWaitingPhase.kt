package net.tjalp.nexus.feature.games.snowballfight

import net.tjalp.nexus.feature.games.GamePhase
import net.tjalp.nexus.feature.games.JoinResult
import org.bukkit.entity.Entity

class SnowballFightWaitingPhase(private val game: SnowballFightGame) : GamePhase {

    val scheduler = game.scheduler.fork("phase/waiting")

    override suspend fun load(previous: GamePhase?) {

    }

    override suspend fun start(previous: GamePhase?) {
    }

    override suspend fun onJoin(entity: Entity): JoinResult {
        return JoinResult.Success
    }

    override fun onLeave(entity: Entity) {

    }

    override fun dispose() {
        scheduler.dispose()
    }
}