package net.tjalp.nexus.player

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
import kotlin.time.ExperimentalTime

/**
 * Redis-backed implementation of PlayerRegistry
 *
 * Uses the following Redis data structures:
 * - Hash: `nexus:player:info:{uuid}` - Player information with TTL
 * - Set: `nexus:server:{serverId}:players` - Players on each server with TTL
 * - Set: `nexus:players:online` - Global set of all online player UUIDs
 *
 * @param redis The Redis controller for pub/sub and data storage
 * @param scope Coroutine scope for launching async operations
 */
@OptIn(ExperimentalLettuceCoroutinesApi::class, ExperimentalTime::class)
class RedisPlayerRegistry(
    private val redis: RedisController,
    private val scope: CoroutineScope
) : PlayerRegistry {

    private val _playerOnlineEvents = MutableSharedFlow<PlayerOnlineEvent>()
    private val _playerOfflineEvents = MutableSharedFlow<PlayerOfflineEvent>()
    private val _playerChangeServerEvents = MutableSharedFlow<PlayerChangeServerEvent>()

    override val playerOnlineEvents: Flow<PlayerOnlineEvent> = _playerOnlineEvents.asSharedFlow()
    override val playerOfflineEvents: Flow<PlayerOfflineEvent> = _playerOfflineEvents.asSharedFlow()
    override val playerChangeServerEvents: Flow<PlayerChangeServerEvent> = _playerChangeServerEvents.asSharedFlow()

    companion object {
        private const val PLAYER_INFO_PREFIX = "nexus:player:info:"
        private const val SERVER_PLAYERS_PREFIX = "nexus:server:players:"
        private const val ONLINE_PLAYERS_SET = "nexus:players:online"
    }

    init {
        // Subscribe to player events
        scope.launch {
            redis.subscribe(Signals.PLAYER_ONLINE).collect { event ->
                _playerOnlineEvents.emit(event)
            }
        }

        scope.launch {
            redis.subscribe(Signals.PLAYER_OFFLINE).collect { event ->
                _playerOfflineEvents.emit(event)
            }
        }

        scope.launch {
            redis.subscribe(Signals.PLAYER_CHANGE_SERVER).collect { event ->
                _playerChangeServerEvents.emit(event)
            }
        }

        // Subscribe to keyspace notifications for player key expirations
        // This detects when player info keys expire (due to server crash or connection loss)
        scope.launch {
            redis.subscribeToKeyExpirations(PLAYER_INFO_PREFIX).collect { expiredKey ->
                val playerIdStr = expiredKey.removePrefix(PLAYER_INFO_PREFIX)

                try {
                    // Player's key expired, clean up references
                    // The player info is already gone, so we just remove from sets
                    redis.query.srem(ONLINE_PLAYERS_SET, playerIdStr)

                    // Note: We don't know which server they were on since the key expired
                    // But that's okay - the per-server set will also expire with the same TTL
                } catch (e: Exception) {
                    // Log but don't crash if cleanup fails
                }
            }
        }
    }

    override suspend fun getPlayer(playerId: UUID): PlayerInfo? {
        val json = redis.query.get(PLAYER_INFO_PREFIX + playerId.toString()) ?: return null
        return try {
            Json.decodeFromString<PlayerInfo>(json)
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun getOnlinePlayers(): Collection<PlayerInfo> {
        val players = mutableListOf<PlayerInfo>()

        redis.query.smembers(ONLINE_PLAYERS_SET).collect { playerIdStr ->
            try {
                val playerId = UUID.fromString(playerIdStr)
                getPlayer(playerId)?.let { players.add(it) }
            } catch (_: Exception) {
                // Ignore invalid UUIDs
            }
        }

        return players
    }

    override suspend fun getPlayersByServer(serverId: String): Collection<PlayerInfo> {
        val players = mutableListOf<PlayerInfo>()

        redis.query.smembers(SERVER_PLAYERS_PREFIX + serverId).collect { playerIdStr ->
            try {
                val playerId = UUID.fromString(playerIdStr)
                getPlayer(playerId)?.let { players.add(it) }
            } catch (_: Exception) {
                // Ignore invalid UUIDs
            }
        }

        return players
    }

    override suspend fun getPlayerCountOnServer(serverId: String): Long {
        return redis.query.scard(SERVER_PLAYERS_PREFIX + serverId) ?: 0L
    }

    override suspend fun getAllPlayers(): Collection<PlayerInfo> {
        // For now, return all online players
        // TODO: To include recently offline players, could maintain a separate set with longer TTL
        return getOnlinePlayers()
    }

    override suspend fun updatePlayerLocation(playerId: UUID, username: String, serverId: String?, ttl: Long) {
        val playerIdStr = playerId.toString()
        val currentPlayer = getPlayer(playerId)

        if (serverId == null) {
            // Player going offline
            if (currentPlayer != null && currentPlayer.serverId != null) {
                // Remove from current server
                redis.query.srem(SERVER_PLAYERS_PREFIX + currentPlayer.serverId, playerIdStr)
                redis.query.srem(ONLINE_PLAYERS_SET, playerIdStr)

                // Delete player info (or set a longer TTL for offline state if desired)
                redis.query.del(PLAYER_INFO_PREFIX + playerIdStr)

                // Publish offline event
                redis.publish(Signals.PLAYER_OFFLINE, PlayerOfflineEvent(playerIdStr, currentPlayer.serverId))
            }
        } else {
            // Player going online or changing servers
            val newPlayer = PlayerInfo(
                id = playerId,
                username = username,
                serverId = serverId,
                lastSeen = kotlin.time.Clock.System.now()
            )

            val json = Json.encodeToString(PlayerInfo.serializer(), newPlayer)

            // Store player info with TTL
            redis.query.setex(PLAYER_INFO_PREFIX + playerIdStr, ttl, json)

            // Add to online players set
            redis.query.sadd(ONLINE_PLAYERS_SET, playerIdStr)

            if (currentPlayer == null) {
                // Player coming online for the first time
                redis.query.sadd(SERVER_PLAYERS_PREFIX + serverId, playerIdStr)
                // Set TTL on the server's player set to auto-cleanup if server crashes
                redis.query.expire(SERVER_PLAYERS_PREFIX + serverId, ttl)
                redis.publish(Signals.PLAYER_ONLINE, PlayerOnlineEvent(newPlayer))
            } else if (currentPlayer.serverId != serverId) {
                // Player changing servers
                if (currentPlayer.serverId != null) {
                    redis.query.srem(SERVER_PLAYERS_PREFIX + currentPlayer.serverId, playerIdStr)
                }
                redis.query.sadd(SERVER_PLAYERS_PREFIX + serverId, playerIdStr)
                // Refresh TTL on the new server's player set
                redis.query.expire(SERVER_PLAYERS_PREFIX + serverId, ttl)
                redis.publish(
                    Signals.PLAYER_CHANGE_SERVER,
                    PlayerChangeServerEvent(playerIdStr, currentPlayer.serverId, serverId)
                )
            } else {
                // Same server, just refresh TTL on the server's player set
                redis.query.expire(SERVER_PLAYERS_PREFIX + serverId, ttl)
            }
        }
    }

    override suspend fun removePlayer(playerId: UUID) {
        val playerIdStr = playerId.toString()
        val currentPlayer = getPlayer(playerId)

        if (currentPlayer != null) {
            // Remove from current server if applicable
            if (currentPlayer.serverId != null) {
                redis.query.srem(SERVER_PLAYERS_PREFIX + currentPlayer.serverId, playerIdStr)
            }

            // Remove from online players set
            redis.query.srem(ONLINE_PLAYERS_SET, playerIdStr)

            // Delete player info
            redis.query.del(PLAYER_INFO_PREFIX + playerIdStr)
        }
    }

    override suspend fun cleanupServerPlayers(serverId: String) {
        // Get all players on this server
        val playerIds = mutableListOf<String>()
        redis.query.smembers(SERVER_PLAYERS_PREFIX + serverId).collect { playerIdStr ->
            playerIds.add(playerIdStr)
        }

        // Remove each player
        for (playerIdStr in playerIds) {
            try {
                UUID.fromString(playerIdStr) // Validate UUID format

                // Publish offline event before cleanup
                redis.publish(Signals.PLAYER_OFFLINE, PlayerOfflineEvent(playerIdStr, serverId))

                // Remove from online players set
                redis.query.srem(ONLINE_PLAYERS_SET, playerIdStr)

                // Delete player info
                redis.query.del(PLAYER_INFO_PREFIX + playerIdStr)
            } catch (_: Exception) {
                // Ignore invalid UUIDs
            }
        }

        // Delete the server players set
        redis.query.del(SERVER_PLAYERS_PREFIX + serverId)
    }

    /**
     * Clean up stale entries from the global online players set
     * This removes player UUIDs where the player info no longer exists
     */
    private suspend fun cleanupStaleOnlinePlayers() {
        val stalePlayerIds = mutableListOf<String>()

        redis.query.smembers(ONLINE_PLAYERS_SET).collect { playerIdStr ->
            // Check if player info still exists
            val exists = redis.query.exists(PLAYER_INFO_PREFIX + playerIdStr) ?: 0L
            if (exists == 0L) {
                stalePlayerIds.add(playerIdStr)
            }
        }

        // Remove stale entries
        if (stalePlayerIds.isNotEmpty()) {
            redis.query.srem(ONLINE_PLAYERS_SET, *stalePlayerIds.toTypedArray())
        }
    }
}




