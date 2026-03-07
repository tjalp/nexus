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
    private val _playerJoinEvents = MutableSharedFlow<PlayerJoinServerEvent>()
    private val _playerLeaveEvents = MutableSharedFlow<PlayerLeaveServerEvent>()

    override val serverOnlineEvents: Flow<ServerOnlineEvent> = _serverOnlineEvents.asSharedFlow()
    override val serverOfflineEvents: Flow<ServerOfflineEvent> = _serverOfflineEvents.asSharedFlow()
    override val playerJoinEvents: Flow<PlayerJoinServerEvent> = _playerJoinEvents.asSharedFlow()
    override val playerLeaveEvents: Flow<PlayerLeaveServerEvent> = _playerLeaveEvents.asSharedFlow()

    companion object {
        private const val SERVER_INFO_PREFIX = "nexus:server:info:"
        private const val SERVER_PLAYERS_PREFIX = "nexus:server:players:"
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

        scope.launch {
            redis.subscribe(Signals.PLAYER_JOIN_SERVER).collect { event ->
                _playerJoinEvents.emit(event)
            }
        }

        scope.launch {
            redis.subscribe(Signals.PLAYER_LEAVE_SERVER).collect { event ->
                _playerLeaveEvents.emit(event)
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

        redis.query.hgetall("$SERVER_INFO_PREFIX*").collect { key ->
            servers += Json.decodeFromString<ServerInfo>(key.value)
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

            // Publish offline event
            redis.publish(Signals.SERVER_OFFLINE, ServerOfflineEvent(serverId))

            // Clean up per-server player set
            redis.query.del(SERVER_PLAYERS_PREFIX + serverId)
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




