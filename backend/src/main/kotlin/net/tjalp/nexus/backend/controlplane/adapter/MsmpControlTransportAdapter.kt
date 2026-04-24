package net.tjalp.nexus.backend.controlplane.adapter

import net.tjalp.nexus.controlplane.*

class MsmpControlTransportAdapter(
    private val enabledServerIds: Set<String>
) : ControlTransportAdapter {
    override val transport: ControlTransport = ControlTransport.MSMP

    override suspend fun isAvailable(server: ManagedServer): Boolean {
        return server.id in enabledServerIds || server.metadata["msmp"] == "true"
    }

    override suspend fun capabilities(server: ManagedServer): Set<ServerCapability> {
        return setOf(
            ServerCapability.PLAYERS_READ,
            ServerCapability.CONSOLE_READ,
            ServerCapability.CONSOLE_WRITE,
            ServerCapability.CUSTOM_ACTION
        )
    }

    override suspend fun listPlayers(server: ManagedServer): List<OnlinePlayer>? {
        // MSMP client wiring is added in a follow-up slice; chain falls back for now.
        return null
    }

    override suspend fun performCustomAction(server: ManagedServer, request: ControlActionRequest): ControlActionResult? {
        return ControlActionResult(
            accepted = false,
            message = "MSMP transport is available for '${server.id}', but custom actions are not wired yet"
        )
    }
}

