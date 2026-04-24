package net.tjalp.nexus.backend.controlplane.adapter

import net.tjalp.nexus.controlplane.*

class RconQueryControlTransportAdapter(
    private val enabledServerIds: Set<String>
) : ControlTransportAdapter {
    override val transport: ControlTransport = ControlTransport.RCON

    override suspend fun isAvailable(server: ManagedServer): Boolean {
        return server.id in enabledServerIds
    }

    override suspend fun capabilities(server: ManagedServer): Set<ServerCapability> {
        return setOf(ServerCapability.PLAYERS_READ, ServerCapability.CONSOLE_WRITE)
    }

    override suspend fun listPlayers(server: ManagedServer): List<OnlinePlayer>? {
        // Query fallback is not wired yet; this keeps chain structure in place.
        return null
    }

    override suspend fun performCustomAction(server: ManagedServer, request: ControlActionRequest): ControlActionResult? {
        return null
    }
}

