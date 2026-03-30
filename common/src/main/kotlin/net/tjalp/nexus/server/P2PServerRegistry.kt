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
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.MulticastSocket
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.seconds

/**
 * P2P-based implementation of ServerRegistry using UDP multicast for discovery
 * and HTTP for server-to-server communication.
 *
 * This implementation is fully decentralized and doesn't require Redis.
 *
 * @param localServer The local server information
 * @param scope Coroutine scope for launching async operations
 * @param apiPort The HTTP API port for server-to-server communication
 * @param multicastGroup The multicast group address for server discovery
 * @param multicastPort The multicast port for server discovery
 */
class P2PServerRegistry(
    private val localServer: ServerInfo,
    private val scope: CoroutineScope,
    private val apiPort: Int = 8080,
    private val multicastGroup: String = "239.255.42.99",
    private val multicastPort: Int = 9999,
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
                prettyPrint = true
            })
        }
        engine {
            requestTimeout = 5000 // 5 seconds
        }
    }

    private var discoveryJob: Job? = null
    private var heartbeatMonitorJob: Job? = null
    private var broadcastJob: Job? = null

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

    @Serializable
    data class DiscoveryMessage(
        val server: ServerInfo,
        val apiPort: Int,
        val messageType: MessageType
    )

    @Serializable
    enum class MessageType {
        ANNOUNCE,
        HEARTBEAT,
        SHUTDOWN
    }

    init {
        // Start listening for multicast discovery messages
        startDiscoveryListener()

        // Start monitoring server health
        startHeartbeatMonitor()

        // Initialize static servers if provided
        scope.launch {
            for (serverUrl in staticServers) {
                try {
                    discoverStaticServer(serverUrl)
                } catch (e: Exception) {
                    // Ignore, will retry later
                }
            }
        }
    }

    override suspend fun getServer(serverId: String): ServerInfo? {
        return servers[serverId]?.takeIf { it.isAlive() }?.info
    }

    override suspend fun getOnlineServers(): Collection<ServerInfo> {
        return servers.values
            .filter { it.isAlive() }
            .map { it.info.copy(online = true) }
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

        // Broadcast this server to the network
        startBroadcasting()

        _serverOnlineEvents.emit(ServerOnlineEvent(server.copy(online = true)))
    }

    override suspend fun unregisterServer(serverId: String) {
        val state = servers.remove(serverId)
        if (state != null) {
            // Broadcast shutdown message
            if (serverId == localServer.id) {
                broadcastMessage(MessageType.SHUTDOWN)
            }
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

            // Broadcast heartbeat if this is the local server
            if (serverId == localServer.id) {
                broadcastMessage(MessageType.HEARTBEAT)
            }
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

    private fun startDiscoveryListener() {
        discoveryJob = scope.launch(Dispatchers.IO) {
            try {
                val multicastSocket = MulticastSocket(multicastPort)
                val group = InetAddress.getByName(multicastGroup)
                multicastSocket.joinGroup(group)

                val buffer = ByteArray(4096)

                while (isActive) {
                    try {
                        val packet = DatagramPacket(buffer, buffer.size)
                        multicastSocket.receive(packet)

                        val message = String(packet.data, 0, packet.length)
                        val discovery = Json.decodeFromString<DiscoveryMessage>(message)

                        // Don't process our own messages
                        if (discovery.server.id == localServer.id) continue

                        handleDiscoveryMessage(discovery)
                    } catch (e: Exception) {
                        if (isActive) {
                            // Log error but continue listening
                        }
                    }
                }

                multicastSocket.leaveGroup(group)
                multicastSocket.close()
            } catch (e: Exception) {
                // Failed to start multicast, fall back to static servers only
            }
        }
    }

    private suspend fun handleDiscoveryMessage(discovery: DiscoveryMessage) {
        val serverId = discovery.server.id
        val apiUrl = buildApiUrl(discovery.server.host, discovery.apiPort)

        when (discovery.messageType) {
            MessageType.ANNOUNCE, MessageType.HEARTBEAT -> {
                val existingState = servers[serverId]
                val newState = ServerState(
                    info = discovery.server,
                    lastHeartbeat = System.currentTimeMillis(),
                    apiUrl = apiUrl
                )

                if (existingState == null) {
                    servers[serverId] = newState
                    _serverOnlineEvents.emit(ServerOnlineEvent(discovery.server.copy(online = true)))
                } else {
                    servers[serverId] = existingState.copy(
                        info = discovery.server,
                        lastHeartbeat = System.currentTimeMillis()
                    )
                }
            }
            MessageType.SHUTDOWN -> {
                servers.remove(serverId)
                _serverOfflineEvents.emit(ServerOfflineEvent(serverId))
            }
        }
    }

    private fun startBroadcasting() {
        if (broadcastJob?.isActive == true) return

        broadcastJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    broadcastMessage(MessageType.ANNOUNCE)
                    delay(10.seconds)
                } catch (e: Exception) {
                    if (isActive) {
                        delay(5.seconds)
                    }
                }
            }
        }
    }

    private fun broadcastMessage(type: MessageType) {
        try {
            val message = DiscoveryMessage(
                server = localServer,
                apiPort = apiPort,
                messageType = type
            )

            val json = Json.encodeToString(DiscoveryMessage.serializer(), message)
            val data = json.toByteArray()

            val socket = MulticastSocket()
            val group = InetAddress.getByName(multicastGroup)
            val packet = DatagramPacket(data, data.length, group, multicastPort)
            socket.send(packet)
            socket.close()
        } catch (e: Exception) {
            // Broadcast failed, but we can still operate with static servers
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

    private suspend fun discoverStaticServer(serverUrl: String) {
        try {
            val response = httpClient.get("$serverUrl/server-info")
            if (response.status == HttpStatusCode.OK) {
                val serverInfo: ServerInfo = response.body()
                val state = ServerState(
                    info = serverInfo,
                    lastHeartbeat = System.currentTimeMillis(),
                    apiUrl = serverUrl
                )
                servers[serverInfo.id] = state
                _serverOnlineEvents.emit(ServerOnlineEvent(serverInfo.copy(online = true)))
            }
        } catch (e: Exception) {
            // Server not reachable, will retry later
        }
    }

    private fun buildApiUrl(host: String, port: Int): String {
        return "http://$host:$port"
    }

    fun dispose() {
        discoveryJob?.cancel()
        heartbeatMonitorJob?.cancel()
        broadcastJob?.cancel()
        httpClient.close()
    }
}
