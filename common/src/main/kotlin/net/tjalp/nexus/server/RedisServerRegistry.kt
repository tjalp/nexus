package net.tjalp.nexus.server

import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import net.tjalp.nexus.redis.RedisController
import net.tjalp.nexus.redis.Signals

/**
 * Redis-backed implementation of ServerRegistry
 *
 * @param redis The Redis controller for pub/sub and data storage
 * @param scope Coroutine scope for launching async operations
 */
@OptIn(ExperimentalLettuceCoroutinesApi::class)
class RedisServerRegistry(
    private val redis: RedisController,
    private val scope: CoroutineScope
) : ServerRegistry {

    private val _serverOnlineEvents = MutableSharedFlow<ServerOnlineEvent>()
    private val _serverOfflineEvents = MutableSharedFlow<ServerOfflineEvent>()

    override val serverOnlineEvents: Flow<ServerOnlineEvent> = _serverOnlineEvents.asSharedFlow()
    override val serverOfflineEvents: Flow<ServerOfflineEvent> = _serverOfflineEvents.asSharedFlow()

    companion object {
        private const val SERVER_INFO_PREFIX = "nexus:server:info:"
    }

    init {
        // Subscribe to server events
        scope.launch {
            redis.subscribe(Signals.SERVER_ONLINE).collect { event ->
                _serverOnlineEvents.emit(event)
            }
        }

        scope.launch {
            redis.subscribe(Signals.SERVER_OFFLINE).collect { event ->
                _serverOfflineEvents.emit(event)
            }
        }

        // Subscribe to keyspace notifications for server key expirations
        // This detects when servers crash and their TTL expires
        scope.launch {
            redis.subscribeToKeyExpirations(SERVER_INFO_PREFIX).collect { expiredKey ->
                val serverId = expiredKey.removePrefix(SERVER_INFO_PREFIX)

                _serverOfflineEvents.emit(ServerOfflineEvent(serverId))
            }
        }
    }

    override suspend fun getServer(serverId: String): ServerInfo? {
        val json = redis.query.get(SERVER_INFO_PREFIX + serverId) ?: return null
        return try {
            Json.decodeFromString<ServerInfo>(json).copy(online = true)
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun getOnlineServers(): Collection<ServerInfo> {
        val servers = mutableListOf<ServerInfo>()

        // Use KEYS to get all server info keys
        // Note: KEYS is blocking, but acceptable for small numbers of servers
        // For large-scale production, consider implementing cursor-based SCAN
        redis.query.keys(SERVER_INFO_PREFIX + "*").collect { key ->
            val json = redis.query.get(key)
            if (json != null) {
                try {
                    servers += Json.decodeFromString<ServerInfo>(json)
                } catch (_: Exception) {
                    // Ignore invalid JSON
                }
            }
        }

        return servers
    }

    override suspend fun getServersByType(type: ServerType): Collection<ServerInfo> {
        return getOnlineServers().filter { it.type == type }
    }

    override suspend fun registerServer(server: ServerInfo) {
        val onlineServer = server.copy(online = true)
        val json = Json.encodeToString(onlineServer)

        // Store server info with 60 second TTL - will expire if heartbeat stops
        redis.query.setex(SERVER_INFO_PREFIX + server.id, 60, json)

        redis.publish(Signals.SERVER_ONLINE, ServerOnlineEvent(onlineServer))
    }

    override suspend fun unregisterServer(serverId: String) {
        val server = getServer(serverId)
        if (server != null) {
            // Delete server info key immediately
            redis.query.del(SERVER_INFO_PREFIX + serverId)

            // Publish offline event – player cleanup is handled by
            // whoever listens to SERVER_OFFLINE (via PlayerRegistry.cleanupServerPlayers)
            redis.publish(Signals.SERVER_OFFLINE, ServerOfflineEvent(serverId))
        }
    }

    override suspend fun updateHeartbeat(serverId: String, playerCount: Int, ttl: Long) {
        val server = getServer(serverId) ?: return
        val updatedServer = server.copy(online = true)
        val json = Json.encodeToString(updatedServer)

        // Refresh server info with TTL
        // If server stops sending heartbeats, this key will expire and server will be automatically removed
        redis.query.setex(SERVER_INFO_PREFIX + serverId, ttl, json)

        redis.publish(Signals.SERVER_HEARTBEAT, ServerHeartbeat(serverId, playerCount))
    }
}




