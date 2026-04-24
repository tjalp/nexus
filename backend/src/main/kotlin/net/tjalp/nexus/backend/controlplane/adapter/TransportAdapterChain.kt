package net.tjalp.nexus.backend.controlplane.adapter

import net.tjalp.nexus.controlplane.*

class TransportAdapterChain(
    private val adapters: List<ControlTransportAdapter>
) {
    suspend fun availableTransports(server: ManagedServer): Set<ControlTransport> {
        val result = linkedSetOf<ControlTransport>()
        for (adapter in adapters) {
            if (adapter.isAvailable(server)) {
                result += adapter.transport
            }
        }
        return result
    }

    suspend fun capabilities(server: ManagedServer): Set<ServerCapability> {
        val capabilities = linkedSetOf<ServerCapability>()
        for (adapter in adapters) {
            if (adapter.isAvailable(server)) {
                capabilities += adapter.capabilities(server)
            }
        }
        return capabilities
    }

    suspend fun listPlayers(server: ManagedServer): List<OnlinePlayer> {
        for (adapter in adapters) {
            if (!adapter.isAvailable(server)) continue
            val players = adapter.listPlayers(server)
            if (players != null) return players
        }
        return emptyList()
    }

    suspend fun performCustomAction(server: ManagedServer, request: ControlActionRequest): ControlActionResult? {
        for (adapter in adapters) {
            if (!adapter.isAvailable(server)) continue
            val result = adapter.performCustomAction(server, request)
            if (result != null) return result
        }
        return null
    }
}

