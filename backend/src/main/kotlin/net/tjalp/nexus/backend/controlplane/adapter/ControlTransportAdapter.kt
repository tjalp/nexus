package net.tjalp.nexus.backend.controlplane.adapter

import net.tjalp.nexus.controlplane.*

interface ControlTransportAdapter {
    val transport: ControlTransport

    suspend fun isAvailable(server: ManagedServer): Boolean

    suspend fun capabilities(server: ManagedServer): Set<ServerCapability>

    suspend fun listPlayers(server: ManagedServer): List<OnlinePlayer>?

    suspend fun performCustomAction(server: ManagedServer, request: ControlActionRequest): ControlActionResult?
}

