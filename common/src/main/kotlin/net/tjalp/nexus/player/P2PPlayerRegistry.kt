package net.tjalp.nexus.player

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import net.tjalp.nexus.server.P2PServerRegistry
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * P2P-based implementation of PlayerRegistry that doesn't require Redis.
 *
 * This implementation maintains a local cache of player information and
 * synchronizes with other servers through HTTP API calls.
 *
 * @param serverRegistry The P2P server registry for server-to-server communication
 * @param localServerId The ID of the local server
 * @param scope Coroutine scope for launching async operations
 * @param apiPort The HTTP API port for server-to-server communication
 */
@OptIn(ExperimentalTime::class)
class P2PPlayerRegistry(
    private val serverRegistry: P2PServerRegistry,
    private val localServerId: String,
    private val scope: CoroutineScope,
    private val apiPort: Int = 8080
) : PlayerRegistry {

    private val _playerOnlineEvents = MutableSharedFlow<PlayerOnlineEvent>()
    private val _playerOfflineEvents = MutableSharedFlow<PlayerOfflineEvent>()
    private val _playerChangeServerEvents = MutableSharedFlow<PlayerChangeServerEvent>()

    override val playerOnlineEvents: Flow<PlayerOnlineEvent> = _playerOnlineEvents.asSharedFlow()
    override val playerOfflineEvents: Flow<PlayerOfflineEvent> = _playerOfflineEvents.asSharedFlow()
    override val playerChangeServerEvents: Flow<PlayerChangeServerEvent> = _playerChangeServerEvents.asSharedFlow()

    // Local cache of all known players across the network
    private val players = ConcurrentHashMap<UUID, PlayerInfo>()

    // Track which players are on our local server
    private val localPlayers = ConcurrentHashMap<UUID, PlayerInfo>()

    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                prettyPrint = false
            })
        }
        engine {
            requestTimeout = 5000
        }
    }

    override suspend fun getPlayer(playerId: UUID): PlayerInfo? {
        // First check local cache
        val cached = players[playerId]
        if (cached != null) {
            return cached
        }

        // Query other servers
        return queryPlayerFromNetwork(playerId)
    }

    override suspend fun getOnlinePlayers(): Collection<PlayerInfo> {
        // Return all players from cache that are still valid
        return players.values.toList()
    }

    override suspend fun getPlayersByServer(serverId: String): Collection<PlayerInfo> {
        if (serverId == localServerId) {
            return localPlayers.values.toList()
        }

        // Query the specific server for its players
        return queryPlayersFromServer(serverId)
    }

    override suspend fun getPlayerCountOnServer(serverId: String): Long {
        if (serverId == localServerId) {
            return localPlayers.size.toLong()
        }

        return queryPlayersFromServer(serverId).size.toLong()
    }

    override suspend fun registerPlayer(playerId: UUID, username: String, serverId: String, ttl: Long) {
        val currentPlayer = players[playerId]

        val newInfo = PlayerInfo(
            id = playerId,
            username = username,
            serverId = serverId,
            status = PlayerStatus.ONLINE,
            lastSeen = Clock.System.now()
        )

        players[playerId] = newInfo

        if (serverId == localServerId) {
            localPlayers[playerId] = newInfo
        } else {
            localPlayers.remove(playerId)
        }

        // Determine which event to emit
        if (currentPlayer == null || currentPlayer.status == PlayerStatus.TRANSFERRING) {
            if (currentPlayer?.serverId != null && currentPlayer.serverId != serverId) {
                // Was transferring from another server
                _playerChangeServerEvents.emit(
                    PlayerChangeServerEvent(playerId.toString(), currentPlayer.serverId, serverId)
                )

                // Notify other servers
                broadcastPlayerEvent(PlayerEventType.CHANGE_SERVER, newInfo, currentPlayer.serverId)
            } else if (currentPlayer == null) {
                // Fresh join
                _playerOnlineEvents.emit(PlayerOnlineEvent(newInfo))

                // Notify other servers
                broadcastPlayerEvent(PlayerEventType.ONLINE, newInfo)
            }
        } else if (currentPlayer.serverId != serverId) {
            // Direct server change
            _playerChangeServerEvents.emit(
                PlayerChangeServerEvent(playerId.toString(), currentPlayer.serverId, serverId)
            )

            // Notify other servers
            broadcastPlayerEvent(PlayerEventType.CHANGE_SERVER, newInfo, currentPlayer.serverId)
        }
    }

    override suspend fun markTransferring(playerId: UUID, ttl: Long) {
        val currentPlayer = players[playerId] ?: return

        val transferringInfo = currentPlayer.copy(
            status = PlayerStatus.TRANSFERRING,
            lastSeen = Clock.System.now()
        )

        players[playerId] = transferringInfo

        // Keep in local players until transfer completes
        if (currentPlayer.serverId == localServerId) {
            localPlayers[playerId] = transferringInfo
        }
    }

    override suspend fun removePlayer(playerId: UUID) {
        val currentPlayer = players.remove(playerId)
        localPlayers.remove(playerId)

        if (currentPlayer != null) {
            _playerOfflineEvents.emit(
                PlayerOfflineEvent(playerId.toString(), currentPlayer.serverId ?: "unknown")
            )

            // Notify other servers
            broadcastPlayerEvent(PlayerEventType.OFFLINE, currentPlayer)
        }
    }

    override suspend fun cleanupServerPlayers(serverId: String) {
        val playersToRemove = players.values.filter { it.serverId == serverId }

        for (player in playersToRemove) {
            players.remove(player.id)
            if (serverId == localServerId) {
                localPlayers.remove(player.id)
            }

            _playerOfflineEvents.emit(
                PlayerOfflineEvent(player.id.toString(), serverId)
            )
        }
    }

    override suspend fun refreshServerPlayersTtl(serverId: String, ttl: Long, onlinePlayerIds: Set<UUID>) {
        // In P2P mode, we don't use TTL-based expiration
        // Instead, we maintain the player list actively

        if (serverId == localServerId) {
            // Update last seen for all local players that are actually online
            for (playerId in onlinePlayerIds) {
                val player = localPlayers[playerId]
                if (player != null && player.status == PlayerStatus.ONLINE) {
                    val updated = player.copy(lastSeen = Clock.System.now())
                    localPlayers[playerId] = updated
                    players[playerId] = updated
                }
            }
        }
    }

    /**
     * Handle incoming player event from another server
     */
    suspend fun handlePlayerEvent(event: PlayerEventMessage) {
        when (event.type) {
            PlayerEventType.ONLINE -> {
                players[event.player.id] = event.player
                _playerOnlineEvents.emit(PlayerOnlineEvent(event.player))
            }
            PlayerEventType.OFFLINE -> {
                players.remove(event.player.id)
                _playerOfflineEvents.emit(
                    PlayerOfflineEvent(event.player.id.toString(), event.player.serverId ?: "unknown")
                )
            }
            PlayerEventType.CHANGE_SERVER -> {
                players[event.player.id] = event.player
                _playerChangeServerEvents.emit(
                    PlayerChangeServerEvent(
                        event.player.id.toString(),
                        event.fromServerId ?: "unknown",
                        event.player.serverId ?: "unknown"
                    )
                )
            }
        }
    }

    private suspend fun queryPlayerFromNetwork(playerId: UUID): PlayerInfo? {
        val servers = serverRegistry.getOnlineServers()

        for (server in servers) {
            if (server.id == localServerId) continue

            try {
                val response = httpClient.get("http://${server.host}:${apiPort}/player/${playerId}")
                if (response.status == HttpStatusCode.OK) {
                    val playerInfo: PlayerInfo = response.body()
                    // Cache the player info locally
                    players[playerId] = playerInfo
                    return playerInfo
                }
            } catch (e: Exception) {
                // Server not reachable or player not found, continue checking other servers
            }
        }

        return null
    }

    private suspend fun queryPlayersFromServer(serverId: String): List<PlayerInfo> {
        val server = serverRegistry.getServer(serverId) ?: return emptyList()

        try {
            val response = httpClient.get("http://${server.host}:${apiPort}/players")
            if (response.status == HttpStatusCode.OK) {
                return response.body()
            }
        } catch (e: Exception) {
            // Server not reachable - this is expected during normal operation
        }

        return emptyList()
    }

    private suspend fun broadcastPlayerEvent(
        type: PlayerEventType,
        player: PlayerInfo,
        fromServerId: String? = null
    ) {
        val event = PlayerEventMessage(
            type = type,
            player = player,
            fromServerId = fromServerId
        )

        val servers = serverRegistry.getOnlineServers()

        for (server in servers) {
            if (server.id == localServerId) continue

            scope.launch {
                try {
                    httpClient.post("http://${server.host}:${apiPort}/player-event") {
                        contentType(ContentType.Application.Json)
                        setBody(event)
                    }
                } catch (e: Exception) {
                    // Failed to notify this server, they'll discover on next query - this is expected during normal operation
                }
            }
        }
    }

    @Serializable
    data class PlayerEventMessage(
        val type: PlayerEventType,
        val player: PlayerInfo,
        val fromServerId: String? = null
    )

    @Serializable
    enum class PlayerEventType {
        ONLINE,
        OFFLINE,
        CHANGE_SERVER
    }

    fun dispose() {
        httpClient.close()
    }
}
