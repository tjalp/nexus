package net.tjalp.nexus.feature.games.snowballfight

import net.kyori.adventure.text.Component.text
import net.tjalp.nexus.NexusPlugin
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
        NexusPlugin.server.sendMessage(text("Loading Snowball Fight Phase"))
    }

    override suspend fun start(previous: GamePhase?) {
        NexusPlugin.server.sendMessage(text("Starting Snowball Fight Phase"))
    }

    override suspend fun onJoin(player: Player): JoinResult {
        return JoinResult.Success
    }

    override fun onLeave(player: Player) {

    }

    override fun dispose() {
        NexusPlugin.server.sendMessage(text("Disposing Snowball Fight Phase"))
    }
}

class SnowballFightWaitingPhase(private val game: SnowballFightGame) : GamePhase {
    override suspend fun load(previous: GamePhase?) {
        NexusPlugin.server.sendMessage(text("Loading Snowball Waiting Phase"))
    }

    override suspend fun start(previous: GamePhase?) {
        NexusPlugin.server.sendMessage(text("Starting Snowball Waiting Phase"))
    }

    override suspend fun onJoin(player: Player): JoinResult {
        return JoinResult.Success
    }

    override fun onLeave(player: Player) {

    }

    override fun dispose() {
        NexusPlugin.server.sendMessage(text("Disposing Snowball Waiting Phase"))
    }
}