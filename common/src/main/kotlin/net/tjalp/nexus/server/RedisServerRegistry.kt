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
import java.util.*

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
        private const val SERVERS_KEY = "nexus:servers"
        private const val PLAYERS_KEY = "nexus:players"
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
        val json = redis.query.hget(SERVERS_KEY, serverId) ?: return null
        return try {
            Json.decodeFromString<ServerInfo>(json)
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun getOnlineServers(): Collection<ServerInfo> {
        val serversMap = mutableListOf<ServerInfo>()
        redis.query.hgetall(SERVERS_KEY).collect { keyValue ->
            try {
                val server = Json.decodeFromString<ServerInfo>(keyValue.value)
                if (server.online) {
                    serversMap.add(server)
                }
            } catch (_: Exception) {
                // Ignore invalid entries
            }
        }
        return serversMap
    }

    override suspend fun getServersByType(type: ServerType): Collection<ServerInfo> {
        return getOnlineServers().filter { it.type == type }
    }

    override suspend fun registerServer(server: ServerInfo) {
        val onlineServer = server.copy(online = true)
        val json = Json.encodeToString(onlineServer)
        redis.query.hset(SERVERS_KEY, server.id, json)
        redis.publish(Signals.SERVER_ONLINE, ServerOnlineEvent(onlineServer))
    }

    override suspend fun unregisterServer(serverId: String) {
        val server = getServer(serverId)
        if (server != null) {
            val offlineServer = server.copy(online = false)
            val json = Json.encodeToString(offlineServer)
            redis.query.hset(SERVERS_KEY, serverId, json)
            redis.publish(Signals.SERVER_OFFLINE, ServerOfflineEvent(serverId))

            // Clean up player tracking for this server
            redis.query.del(SERVER_PLAYERS_PREFIX + serverId)
        }
    }

    override suspend fun updateHeartbeat(serverId: String, playerCount: Int) {
        val server = getServer(serverId) ?: return
        val updatedServer = server.copy(online = true)
        val json = Json.encodeToString(updatedServer)
        redis.query.hset(SERVERS_KEY, serverId, json)
        redis.publish(Signals.SERVER_HEARTBEAT, ServerHeartbeat(serverId, playerCount))
    }

    override suspend fun getPlayerServer(playerId: UUID): String? {
        return redis.query.hget(PLAYERS_KEY, playerId.toString())
    }

    override suspend fun setPlayerServer(playerId: UUID, serverId: String?) {
        val playerIdStr = playerId.toString()

        if (serverId == null) {
            // Remove player
            val currentServer = redis.query.hget(PLAYERS_KEY, playerIdStr)
            redis.query.hdel(PLAYERS_KEY, playerIdStr)

            if (currentServer != null) {
                redis.query.srem(SERVER_PLAYERS_PREFIX + currentServer, playerIdStr)
                redis.publish(Signals.PLAYER_LEAVE_SERVER, PlayerLeaveServerEvent(playerIdStr, currentServer))
            }
        } else {
            // Get previous server if any
            val previousServer = redis.query.hget(PLAYERS_KEY, playerIdStr)

            // Remove from previous server
            if (previousServer != null && previousServer != serverId) {
                redis.query.srem(SERVER_PLAYERS_PREFIX + previousServer, playerIdStr)
                redis.publish(Signals.PLAYER_LEAVE_SERVER, PlayerLeaveServerEvent(playerIdStr, previousServer))
            }

            // Add to new server
            redis.query.hset(PLAYERS_KEY, playerIdStr, serverId)
            redis.query.sadd(SERVER_PLAYERS_PREFIX + serverId, playerIdStr)
            redis.publish(Signals.PLAYER_JOIN_SERVER, PlayerJoinServerEvent(playerIdStr, serverId))
        }
    }

    override suspend fun getPlayersOnServer(serverId: String): Collection<UUID> {
        val playerIds = mutableListOf<UUID>()
        redis.query.smembers(SERVER_PLAYERS_PREFIX + serverId).collect { playerIdStr ->
            try {
                playerIds.add(UUID.fromString(playerIdStr))
            } catch (_: Exception) {
                // Ignore invalid UUIDs
            }
        }
        return playerIds
    }
}




