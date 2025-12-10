package net.tjalp.nexus.feature.games.snowballfight

import net.kyori.adventure.text.Component.text
import net.tjalp.nexus.feature.games.Game
import net.tjalp.nexus.feature.games.GamePhase
import net.tjalp.nexus.feature.games.GameType
import net.tjalp.nexus.feature.games.JoinResult
import org.bukkit.entity.Player

class SnowballFightGame : Game(type = GameType.SNOWBALL_FIGHT) {

    override val nextPhase: GamePhase
        get() = if (currentPhase == null || currentPhase is SnowballFightPhase) {
            SnowballFightWaitingPhase(this)
        } else {
            SnowballFightPhase(this)
        }
}

class SnowballFightPhase(private val game: SnowballFightGame) : GamePhase {

    val scheduler = game.scheduler.fork("phase/fight")

    override suspend fun load(previous: GamePhase?) {
        scheduler.repeat(interval = 15) {
            game.participants.forEach { player ->
                player.sendActionBar(text("Throw snowballs at your opponents!"))
            }
        }
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

class SnowballFightWaitingPhase(private val game: SnowballFightGame) : GamePhase {

    val scheduler = game.scheduler.fork("phase/waiting")

    override suspend fun load(previous: GamePhase?) {
        scheduler.repeat(interval = 15) {
            game.participants.forEach { player ->
                player.sendActionBar(text("Waiting for the game to start..."))
            }
        }
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