package net.tjalp.nexus.backend.controlplane

import net.tjalp.nexus.controlplane.*
import kotlinx.coroutines.flow.Flow

class ControlPlaneService(
    private val providers: List<ControlPlaneProvider>,
    private val eventHub: ControlPlaneEventHub
) {
    fun listProviders(): List<String> = providers.map { it.providerId }

    suspend fun listStacks(): List<StackSummary> = providers.flatMap { it.listStacks() }

    suspend fun listServers(stackId: String): List<ManagedServer> {
        return providers.flatMap { provider ->
            provider.listServers(stackId)
        }
    }

    suspend fun listAllServers(): List<ManagedServer> {
        val stacks = listStacks()
        return stacks.flatMap { stack -> listServers(stack.id) }
    }

    suspend fun getServer(serverId: String): ManagedServer? {
        return resolveProvider(serverId)?.getServer(serverId)
    }

    suspend fun listPlayers(serverId: String): List<OnlinePlayer> {
        val provider = resolveProvider(serverId) ?: return emptyList()
        return provider.listPlayers(serverId)
    }

    suspend fun performAction(serverId: String, request: ControlActionRequest): ControlActionResult {
        val provider = resolveProvider(serverId)
        if (provider != null) {
            return provider.performAction(serverId, request)
        }

        return ControlActionResult(
            accepted = false,
            message = "Server '$serverId' was not found in any control-plane provider"
        )
    }

    fun subscribe(serverId: String): Flow<ControlPlaneEvent> = eventHub.subscribe(serverId)

    private suspend fun resolveProvider(serverId: String): ControlPlaneProvider? {
        for (provider in providers) {
            val server = provider.getServer(serverId)
            if (server != null) {
                return provider
            }
        }

        return null
    }
}

