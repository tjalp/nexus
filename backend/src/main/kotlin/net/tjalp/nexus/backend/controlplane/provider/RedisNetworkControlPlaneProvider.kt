package net.tjalp.nexus.backend.controlplane.provider

import kotlinx.coroutines.CoroutineScope
import net.tjalp.nexus.controlplane.*
import net.tjalp.nexus.player.RedisPlayerRegistry
import net.tjalp.nexus.redis.RedisController
import net.tjalp.nexus.server.RedisServerRegistry

/**
 * Provider that exposes currently-online Nexus servers via Redis registries.
 *
 * This is a read-focused bootstrap provider: it lists stacks/servers/players so the panel can
 * render real data now, while lifecycle actions are implemented in dedicated runtime providers.
 */
class RedisNetworkControlPlaneProvider(
    redis: RedisController,
    scope: CoroutineScope
) : ControlPlaneProvider {
    private val serverRegistry = RedisServerRegistry(redis, scope)
    private val playerRegistry = RedisPlayerRegistry(redis, scope)

    override val providerId: String = "redis-network"

    override suspend fun listStacks(): List<StackSummary> {
        return listOf(
            StackSummary(
                id = STACK_ID,
                name = "Network",
                runtimeType = RuntimeType.BARE_METAL,
                hostId = "default"
            )
        )
    }

    override suspend fun listServers(stackId: String): List<ManagedServer> {
        if (stackId != STACK_ID) return emptyList()

        return serverRegistry.getOnlineServers().map { server ->
            ManagedServer(
                id = server.id,
                stackId = STACK_ID,
                name = server.name,
                state = ManagedServerState.RUNNING,
                transports = setOf(ControlTransport.PLUGIN, ControlTransport.QUERY),
                capabilities = setOf(ServerCapability.PLAYERS_READ)
            )
        }
    }

    override suspend fun getServer(serverId: String): ManagedServer? {
        val server = serverRegistry.getServer(serverId) ?: return null

        return ManagedServer(
            id = server.id,
            stackId = STACK_ID,
            name = server.name,
            state = ManagedServerState.RUNNING,
            transports = setOf(ControlTransport.PLUGIN, ControlTransport.QUERY),
            capabilities = setOf(ServerCapability.PLAYERS_READ)
        )
    }

    override suspend fun listPlayers(serverId: String): List<OnlinePlayer> {
        return playerRegistry.getPlayersByServer(serverId).map { player ->
            OnlinePlayer(
                id = player.id.toString(),
                username = player.username
            )
        }
    }

    override suspend fun performAction(serverId: String, request: ControlActionRequest): ControlActionResult {
        val server = serverRegistry.getServer(serverId)
        if (server == null) {
            return ControlActionResult(accepted = false, message = "Server '$serverId' is offline or unknown")
        }

        val actionName = if (request.action == ServerAction.CUSTOM) {
            request.customAction ?: "custom"
        } else {
            request.action.name.lowercase()
        }

        return ControlActionResult(
            accepted = false,
            message = "Action '$actionName' is not yet implemented for provider '$providerId'"
        )
    }

    private companion object {
        const val STACK_ID = "network"
    }
}

