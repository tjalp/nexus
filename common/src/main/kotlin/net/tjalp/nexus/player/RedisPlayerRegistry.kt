package net.tjalp.nexus.player

import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.ScanArgs
import io.lettuce.core.ScanCursor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import net.tjalp.nexus.redis.RedisController
import net.tjalp.nexus.redis.Signals
import java.util.*
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Redis-backed implementation of PlayerRegistry.
 *
 * **Single source of truth:** The per-player key `nexus:player:info:{uuid}` is the
 * authoritative record. It stores serialized [PlayerInfo] (including serverId and status)
 * and has a TTL aligned with the server heartbeat so it auto-expires on crash.
 *
 * **Derived index:** The set `nexus:server:players:{serverId}` is a secondary index
 * kept in sync by this class. It is always rebuildable from the player info keys.
 * Its TTL matches the server heartbeat so it also self-cleans on crash.
 *
 * There is **no global online-players set**. Listing all online players uses SCAN
 * over the `nexus:player:info:*` keyspace, which is crash-safe because every key
 * has a TTL.
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
        /** Per-player info key. This is the single source of truth. */
        private const val PLAYER_INFO_PREFIX = "nexus:player:info:"

        /** Per-server player set – derived index only. */
        private const val SERVER_PLAYERS_PREFIX = "nexus:server:players:"
    }

    init {
        // Subscribe to player events via pub/sub
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

        // Listen for player info key expirations (server crash / transfer timeout).
        // When a player info key expires the player is gone – the per-server set also
        // has a TTL so it will self-expire. No action needed here beyond observing.
        scope.launch {
            redis.subscribeToKeyExpirations(PLAYER_INFO_PREFIX).collect { _ ->
                // The per-server set has a matching TTL, so it self-cleans.
                // We could attempt to remove from derived indices here, but the
                // key is already gone so we don't know the serverId. This is fine.
            }
        }
    }

    // ── Queries ──────────────────────────────────────────────────────────────

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
        val scanArgs = ScanArgs.Builder.matches(PLAYER_INFO_PREFIX + "*").limit(100)
        var cursor = redis.query.scan(scanArgs)

        while (cursor != null) {
            for (key in cursor.keys) {
                val json = redis.query.get(key)
                if (json != null) {
                    try {
                        players += Json.decodeFromString<PlayerInfo>(json)
                    } catch (_: Exception) { /* skip invalid */ }
                }
            }
            if (cursor.isFinished) break
            cursor = redis.query.scan(ScanCursor.of(cursor.cursor), scanArgs)
        }

        return players
    }

    override suspend fun getPlayersByServer(serverId: String): Collection<PlayerInfo> {
        val players = mutableListOf<PlayerInfo>()

        redis.query.smembers(SERVER_PLAYERS_PREFIX + serverId).collect { playerIdStr ->
            // Validate against the source of truth
            val info = getPlayer(safeUuid(playerIdStr) ?: return@collect)
            if (info != null) {
                players += info
            } else {
                // Stale entry in the set – remove it
                redis.query.srem(SERVER_PLAYERS_PREFIX + serverId, playerIdStr)
            }
        }

        return players
    }

    override suspend fun getPlayerCountOnServer(serverId: String): Long {
        return redis.query.scard(SERVER_PLAYERS_PREFIX + serverId) ?: 0L
    }

    // ── Mutations ────────────────────────────────────────────────────────────

    override suspend fun registerPlayer(playerId: UUID, username: String, serverId: String, ttl: Long) {
        val playerIdStr = playerId.toString()
        val currentPlayer = getPlayer(playerId)

        val newInfo = PlayerInfo(
            id = playerId,
            username = username,
            serverId = serverId,
            status = PlayerStatus.ONLINE,
            lastSeen = Clock.System.now()
        )

        val json = Json.encodeToString(PlayerInfo.serializer(), newInfo)

        // Write the single source of truth with TTL
        redis.query.setex(PLAYER_INFO_PREFIX + playerIdStr, ttl, json)

        // Update derived index
        if (currentPlayer != null && currentPlayer.serverId != null && currentPlayer.serverId != serverId) {
            // Changing servers – remove from old server set
            redis.query.srem(SERVER_PLAYERS_PREFIX + currentPlayer.serverId, playerIdStr)
        }
        redis.query.sadd(SERVER_PLAYERS_PREFIX + serverId, playerIdStr)
        redis.query.expire(SERVER_PLAYERS_PREFIX + serverId, ttl)

        // Publish appropriate event
        if (currentPlayer == null || currentPlayer.status == PlayerStatus.TRANSFERRING) {
            if (currentPlayer?.serverId != null && currentPlayer.serverId != serverId) {
                // Was transferring from another server
                redis.publish(
                    Signals.PLAYER_CHANGE_SERVER,
                    PlayerChangeServerEvent(playerIdStr, currentPlayer.serverId, serverId)
                )
            } else if (currentPlayer == null) {
                // Fresh join
                redis.publish(Signals.PLAYER_ONLINE, PlayerOnlineEvent(newInfo))
            }
            // If transferring to the same server (reconnect), just update silently
        } else if (currentPlayer.serverId != serverId) {
            // Was online on a different server (direct server change without transfer flag)
            redis.query.srem(SERVER_PLAYERS_PREFIX + (currentPlayer.serverId ?: ""), playerIdStr)
            redis.publish(
                Signals.PLAYER_CHANGE_SERVER,
                PlayerChangeServerEvent(playerIdStr, currentPlayer.serverId, serverId)
            )
        }
        // If same server and already ONLINE, this is just a heartbeat refresh – no event needed
    }

    override suspend fun markTransferring(playerId: UUID, ttl: Long) {
        val playerIdStr = playerId.toString()
        val currentPlayer = getPlayer(playerId) ?: return

        val transferringInfo = currentPlayer.copy(
            status = PlayerStatus.TRANSFERRING,
            lastSeen = Clock.System.now()
        )

        val json = Json.encodeToString(PlayerInfo.serializer(), transferringInfo)

        // Update the source of truth with a shorter TTL so it auto-expires if the transfer fails
        redis.query.setex(PLAYER_INFO_PREFIX + playerIdStr, ttl, json)

        // Keep them in the per-server set for now – they'll be moved on registerPlayer()
        // or cleaned up by TTL expiration if the transfer fails
    }

    override suspend fun removePlayer(playerId: UUID) {
        val playerIdStr = playerId.toString()
        val currentPlayer = getPlayer(playerId)

        if (currentPlayer != null) {
            // Remove from derived index
            if (currentPlayer.serverId != null) {
                redis.query.srem(SERVER_PLAYERS_PREFIX + currentPlayer.serverId, playerIdStr)
            }

            // Delete the source of truth
            redis.query.del(PLAYER_INFO_PREFIX + playerIdStr)

            // Publish offline event
            redis.publish(
                Signals.PLAYER_OFFLINE,
                PlayerOfflineEvent(playerIdStr, currentPlayer.serverId ?: "unknown")
            )
        }
    }

    override suspend fun cleanupServerPlayers(serverId: String) {
        val playerIds = mutableListOf<String>()
        redis.query.smembers(SERVER_PLAYERS_PREFIX + serverId).collect { playerIdStr ->
            playerIds += playerIdStr
        }

        for (playerIdStr in playerIds) {
            try {
                // Publish offline event
                redis.publish(Signals.PLAYER_OFFLINE, PlayerOfflineEvent(playerIdStr, serverId))

                // Delete the source of truth
                redis.query.del(PLAYER_INFO_PREFIX + playerIdStr)
            } catch (_: Exception) {
                // Ignore errors during cleanup
            }
        }

        // Delete the derived index
        redis.query.del(SERVER_PLAYERS_PREFIX + serverId)
    }

    override suspend fun refreshServerPlayersTtl(serverId: String, ttl: Long, onlinePlayerIds: Set<UUID>) {
        // Refresh TTL on the derived index
        redis.query.expire(SERVER_PLAYERS_PREFIX + serverId, ttl)

        onlinePlayerIds.forEach { id ->
            try {
                val key = PLAYER_INFO_PREFIX + id
                val exists = redis.query.exists(key) ?: 0L

                if (exists > 0L) {
                    redis.query.expire(key, ttl)
                    return@forEach
                }

                // Key expired – clean up the stale set entry
                redis.query.srem(SERVER_PLAYERS_PREFIX + serverId, id.toString())
            } catch (_: Exception) {}
        }
//        redis.query.smembers(SERVER_PLAYERS_PREFIX + serverId).collect { playerIdStr ->
//            try {
//                val uuid = safeUuid(playerIdStr) ?: run {
//                    redis.query.srem(SERVER_PLAYERS_PREFIX + serverId, playerIdStr)
//                    return@collect
//                }
//
//                if (uuid !in onlinePlayerIds) return@collect
//
//                val key = PLAYER_INFO_PREFIX + playerIdStr
//                val exists = redis.query.exists(key) ?: 0L
//                if (exists > 0L) {
//                    redis.query.expire(key, ttl)
//                } else {
//                    // Key expired – clean up the stale set entry
//                    redis.query.srem(SERVER_PLAYERS_PREFIX + serverId, playerIdStr)
//                }
//            } catch (_: Exception) {
//                // Ignore individual refresh failures
//            }
//        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun safeUuid(str: String): UUID? = try {
        UUID.fromString(str)
    } catch (_: Exception) {
        null
    }
}




