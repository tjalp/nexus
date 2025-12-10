package net.tjalp.nexus.feature.games.snowballfight

import net.tjalp.nexus.feature.games.GamePhase
import net.tjalp.nexus.feature.games.JoinResult
import org.bukkit.entity.Player

class SnowballFightWaitingPhase(private val game: SnowballFightGame) : GamePhase {

    val scheduler = game.scheduler.fork("phase/waiting")

    override suspend fun load(previous: GamePhase?) {

    }

    override suspend fun start(previous: GamePhase?) {
    }

    override suspend fun onJoin(player: Player): JoinResult {
        return JoinResult.Success
    }

    override fun onLeave(player: Player) {

    }

    override fun dispose() {
        scheduler.dispose()
    }
}