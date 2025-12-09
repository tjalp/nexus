package net.tjalp.nexus.feature.games.snowballfight

import net.kyori.adventure.text.Component.text
import net.tjalp.nexus.NexusPlugin
import net.tjalp.nexus.feature.games.Game
import net.tjalp.nexus.feature.games.GamePhase
import net.tjalp.nexus.feature.games.GameType
import org.bukkit.entity.Player

class SnowballFightGame : Game(type = GameType.SNOWBALL_FIGHT) {

    override val nextPhase: GamePhase
        get() = if (currentPhase == null || currentPhase is SnowballFightPhase) {
            SnowballFightWaitingPhase()
        } else {
            SnowballFightPhase()
        }
}

class SnowballFightPhase : GamePhase {
    override suspend fun load(previous: GamePhase?) {
        NexusPlugin.server.sendMessage(text("Loading Snowball Fight Phase"))
    }

    override suspend fun start(previous: GamePhase?) {
        NexusPlugin.server.sendMessage(text("Starting Snowball Fight Phase"))
    }

    override suspend fun join(player: Player) {
        TODO("Not yet implemented")
    }

    override suspend fun leave(player: Player) {
        TODO("Not yet implemented")
    }

    override fun dispose() {
        NexusPlugin.server.sendMessage(text("Disposing Snowball Fight Phase"))
    }
}

class SnowballFightWaitingPhase : GamePhase {
    override suspend fun load(previous: GamePhase?) {
        NexusPlugin.server.sendMessage(text("Loading Snowball Waiting Phase"))
    }

    override suspend fun start(previous: GamePhase?) {
        NexusPlugin.server.sendMessage(text("Starting Snowball Waiting Phase"))
    }

    override suspend fun join(player: Player) {
        TODO("Not yet implemented")
    }

    override suspend fun leave(player: Player) {
        TODO("Not yet implemented")
    }

    override fun dispose() {
        NexusPlugin.server.sendMessage(text("Disposing Snowball Waiting Phase"))
    }
}