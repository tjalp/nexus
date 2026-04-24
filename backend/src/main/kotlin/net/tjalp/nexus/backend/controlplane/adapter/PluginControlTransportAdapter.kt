package net.tjalp.nexus.backend.controlplane.adapter

import net.tjalp.nexus.controlplane.*
import net.tjalp.nexus.player.PlayerRegistry

class PluginControlTransportAdapter(
    private val playerRegistry: PlayerRegistry
) : ControlTransportAdapter {
    override val transport: ControlTransport = ControlTransport.PLUGIN

    override suspend fun isAvailable(server: ManagedServer): Boolean = true

    override suspend fun capabilities(server: ManagedServer): Set<ServerCapability> {
        return setOf(ServerCapability.PLAYERS_READ)
    }

    override suspend fun listPlayers(server: ManagedServer): List<OnlinePlayer>? {
        return playerRegistry.getPlayersByServer(server.id).map { player ->
            OnlinePlayer(
                id = player.id.toString(),
                username = player.username
            )
        }
    }

    override suspend fun performCustomAction(server: ManagedServer, request: ControlActionRequest): ControlActionResult? {
        return null
    }
}

