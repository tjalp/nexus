package net.tjalp.nexus.server

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.seconds

/**
 * P2P-based implementation of ServerRegistry using HTTP for discovery
 * and server-to-server communication.
 *
 * This implementation is fully decentralized and doesn't require Redis.
 * It works over the internet and in Docker containers.
 *
 * Discovery methods:
 * 1. Static server list (primary) - configured list of known servers
 * 2. HTTP polling - each server exposes /servers endpoint listing known peers
 * 3. Gossip protocol - servers share their known peers with each other
 *
 * @param localServer The local server information
 * @param scope Coroutine scope for launching async operations
 * @param apiPort The HTTP API port for server-to-server communication
 * @param staticServers List of known server URLs to connect to
 */
class P2PServerRegistry(
    private val localServer: ServerInfo,
    private val scope: CoroutineScope,
    private val apiPort: Int = 8080,
    private val staticServers: List<String> = emptyList()
) : ServerRegistry {

    private val _serverOnlineEvents = MutableSharedFlow<ServerOnlineEvent>()
    private val _serverOfflineEvents = MutableSharedFlow<ServerOfflineEvent>()

    override val serverOnlineEvents: Flow<ServerOnlineEvent> = _serverOnlineEvents.asSharedFlow()
    override val serverOfflineEvents: Flow<ServerOfflineEvent> = _serverOfflineEvents.asSharedFlow()

    // Track all known servers with their last heartbeat time
    private val servers = ConcurrentHashMap<String, ServerState>()

    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                prettyPrint = false
            })
        }
        engine {
            requestTimeout = 5000 // 5 seconds
        }
    }

    private var discoveryJob: Job? = null
    private var heartbeatMonitorJob: Job? = null

    @Serializable
    private data class ServerState(
        val info: ServerInfo,
        val lastHeartbeat: Long,
        val apiUrl: String,
        @kotlinx.serialization.Transient var playerCount: Int = 0
    ) {
        fun isAlive(timeoutMs: Long = 30000): Boolean {
            return System.currentTimeMillis() - lastHeartbeat < timeoutMs
        }
    }

    init {
        // Start polling known servers for discovery
        startDiscoveryPolling()

        // Start monitoring server health
        startHeartbeatMonitor()
    }

    override suspend fun getServer(serverId: String): ServerInfo? {
        return servers[serverId]?.takeIf { it.isAlive() }?.info
    }

    override suspend fun getOnlineServers(): Collection<ServerInfo> {
        return servers.values
            .filter { it.isAlive() }
            .map { it.info }
    }

    override suspend fun getServersByType(type: ServerType): Collection<ServerInfo> {
        return getOnlineServers().filter { it.type == type }
    }

    override suspend fun registerServer(server: ServerInfo) {
        val apiUrl = buildApiUrl(server.host, apiPort)
        servers[server.id] = ServerState(
            info = server,
            lastHeartbeat = System.currentTimeMillis(),
            apiUrl = apiUrl
        )

        _serverOnlineEvents.emit(ServerOnlineEvent(server))

        // Announce ourselves to all static servers
        announceToStaticServers()
    }

    override suspend fun unregisterServer(serverId: String) {
        val state = servers.remove(serverId)
        if (state != null) {
            _serverOfflineEvents.emit(ServerOfflineEvent(serverId))
        }
    }

    override suspend fun updateHeartbeat(serverId: String, playerCount: Int, ttl: Long) {
        val state = servers[serverId]
        if (state != null) {
            servers[serverId] = state.copy(
                lastHeartbeat = System.currentTimeMillis(),
                playerCount = playerCount
            )
        }
    }

    /**
     * Check if a server is available and has capacity for a player
     */
    suspend fun checkServerAvailability(serverId: String): ServerAvailability {
        val state = servers[serverId] ?: return ServerAvailability(false, "Server not found")

        if (!state.isAlive()) {
            return ServerAvailability(false, "Server is offline")
        }

        try {
            val response = httpClient.get("${state.apiUrl}/health") {
                timeout {
                    requestTimeoutMillis = 3000
                }
            }

            if (response.status != HttpStatusCode.OK) {
                return ServerAvailability(false, "Server health check failed")
            }

            val health: HealthResponse = response.body()

            // Check if server has capacity
            if (state.info.maxPlayers > 0 && health.playerCount >= state.info.maxPlayers) {
                return ServerAvailability(false, "Server is full")
            }

            return ServerAvailability(true, "Available", health.playerCount)
        } catch (e: Exception) {
            return ServerAvailability(false, "Failed to reach server: ${e.message}")
        }
    }

    @Serializable
    data class ServerAvailability(
        val available: Boolean,
        val reason: String,
        val playerCount: Int = 0
    )

    @Serializable
    data class HealthResponse(
        val healthy: Boolean,
        val playerCount: Int,
        val maxPlayers: Int
    )

    /**
     * Start polling static servers and discovered peers for network discovery
     */
    private fun startDiscoveryPolling() {
        discoveryJob = scope.launch {
            while (isActive) {
                try {
                    // Poll static servers
                    for (serverUrl in staticServers) {
                        discoverServer(serverUrl)
                    }

                    // Poll known servers for their peer lists (gossip protocol)
                    val knownServers = servers.values.toList()
                    for (state in knownServers) {
                        if (state.info.id != localServer.id) {
                            pollServerPeers(state.apiUrl)
                        }
                    }

                    delay(10.seconds)
                } catch (e: Exception) {
                    // Log unexpected errors during discovery polling
                    println("Error during discovery polling: ${e.message}")
                    if (isActive) {
                        delay(5.seconds)
                    }
                }
            }
        }
    }

    /**
     * Discover a server at the given URL
     */
    private suspend fun discoverServer(serverUrl: String) {
        try {
            val response = httpClient.get("$serverUrl/server-info")
            if (response.status == HttpStatusCode.OK) {
                val serverInfo: ServerInfo = response.body()

                // Don't add ourselves
                if (serverInfo.id == localServer.id) return

                val existingState = servers[serverInfo.id]
                val newState = ServerState(
                    info = serverInfo,
                    lastHeartbeat = System.currentTimeMillis(),
                    apiUrl = serverUrl
                )

                if (existingState == null) {
                    servers[serverInfo.id] = newState
                    _serverOnlineEvents.emit(ServerOnlineEvent(serverInfo))
                } else {
                    servers[serverInfo.id] = existingState.copy(
                        info = serverInfo,
                        lastHeartbeat = System.currentTimeMillis()
                    )
                }
            }
        } catch (e: Exception) {
            // Server not reachable, will retry on next poll - this is expected during normal operation
        }
    }

    /**
     * Poll a server for its list of known peers (gossip protocol)
     */
    private suspend fun pollServerPeers(serverUrl: String) {
        try {
            val response = httpClient.get("$serverUrl/servers")
            if (response.status == HttpStatusCode.OK) {
                val peerServers: List<ServerInfo> = response.body()

                for (peerInfo in peerServers) {
                    // Don't add ourselves
                    if (peerInfo.id == localServer.id) continue

                    // Try to discover this peer if we don't know about it
                    if (!servers.containsKey(peerInfo.id)) {
                        val peerUrl = buildApiUrl(peerInfo.host, apiPort)
                        discoverServer(peerUrl)
                    }
                }
            }
        } catch (e: Exception) {
            // Peer query failed, continue - this is expected during normal operation
        }
    }

    /**
     * Announce our presence to all static servers
     */
    private suspend fun announceToStaticServers() {
        for (serverUrl in staticServers) {
            try {
                // Simply polling the server will make them aware of us via their /servers endpoint
                discoverServer(serverUrl)
            } catch (e: Exception) {
                // Continue with other servers - errors are already handled in discoverServer
            }
        }
    }

    private fun startHeartbeatMonitor() {
        heartbeatMonitorJob = scope.launch {
            while (isActive) {
                delay(15.seconds)

                val now = System.currentTimeMillis()
                val deadServers = servers.values.filter { !it.isAlive(30000) }

                for (state in deadServers) {
                    servers.remove(state.info.id)
                    _serverOfflineEvents.emit(ServerOfflineEvent(state.info.id))
                }
            }
        }
    }

    private fun buildApiUrl(host: String, port: Int): String {
        return "http://$host:$port"
    }

    fun dispose() {
        discoveryJob?.cancel()
        heartbeatMonitorJob?.cancel()
        httpClient.close()
    }
}
