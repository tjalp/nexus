package net.tjalp.nexus.feature.servers

import io.papermc.paper.connection.PlayerLoginConnection
import io.papermc.paper.event.connection.PlayerConnectionValidateLoginEvent
import kotlinx.coroutines.*
import kotlinx.coroutines.future.await
import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.Component.translatable
import net.kyori.adventure.text.format.NamedTextColor.RED
import net.tjalp.nexus.Feature
import net.tjalp.nexus.NexusPlugin
import net.tjalp.nexus.feature.FeatureKeys.SERVERS
import net.tjalp.nexus.player.PlayerRegistry
import net.tjalp.nexus.player.PlayerStatus
import net.tjalp.nexus.player.RedisPlayerRegistry
import net.tjalp.nexus.redis.RedisConfig
import net.tjalp.nexus.redis.RedisController
import net.tjalp.nexus.server.RedisServerRegistry
import net.tjalp.nexus.server.ServerInfo
import net.tjalp.nexus.server.ServerRegistry
import net.tjalp.nexus.server.ServerType
import net.tjalp.nexus.transfer.TransferToken
import net.tjalp.nexus.transfer.TransferTokenSigner
import net.tjalp.nexus.util.register
import net.tjalp.nexus.util.unregister
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.seconds

/**
 * Feature for managing multiserver infrastructure with Redis.
 *
 * Network transitions:
 * - Starts in [NetworkState.DEGRADED] while Redis is unavailable.
 * - Transitions to [NetworkState.ONLINE] once Redis becomes reachable.
 * - Returns to [NetworkState.DEGRADED] if Redis becomes unreachable during operation.
 *
 * While [NetworkState.DEGRADED]:
 * - Cross-server session checks are skipped (player may join freely).
 * - Transfers are disabled.
 *
 * While [NetworkState.ONLINE]:
 * - Full session enforcement via Redis registries.
 * - Transfers use a short-lived signed cookie ([TransferToken]) validated on the target.
 */
@Suppress("UnstableApiUsage")
class ServersFeature : Feature(SERVERS), Listener {

    // ── Public properties ─────────────────────────────────────────────────────

    var networkState: NetworkState = NetworkState.DEGRADED
        private set

    var serverRegistry: ServerRegistry? = null
        private set

    var playerRegistry: PlayerRegistry? = null
        private set

    lateinit var serverInfo: ServerInfo
        private set

    var globalChat: GlobalChatHandler? = null
        private set

    // ── Private state ─────────────────────────────────────────────────────────

    private var heartbeatJob: Job? = null
    private var initialConnectJob: Job? = null
    private var redisConnectionEventsJob: Job? = null
    private var pendingOnlineTransitionJob: Job? = null
    private var pendingDegradedTransitionJob: Job? = null
    private val registryEventJobs = mutableListOf<Job>()
    private val networkTransitionInProgress = AtomicBoolean(false)
    private var heartbeatTtl: Long = 60
    private lateinit var tokenSigner: TransferTokenSigner
    private lateinit var transferCookieKey: NamespacedKey

    companion object {
        private val CONNECTION_STATE_STABILIZATION_DELAY = 1.seconds
    }

    override fun onEnable() {
        val config = NexusPlugin.configuration.features.servers

        // Warn if default cookie secret is still in use
        if (config.transferCookieSecret == TransferTokenSigner.DEFAULT_SECRET) {
            NexusPlugin.logger.warning(
                "[Servers] transfer_cookie_secret is set to the default value! " +
                "Change it in config.yml to a unique shared secret for your network."
            )
        }

        // Parse server type from config
        val serverType = try {
            ServerType.valueOf(config.serverType.uppercase())
        } catch (_: IllegalArgumentException) {
            NexusPlugin.logger.warning("Invalid server type '${config.serverType}', defaulting to OTHER")
            ServerType.OTHER
        }

        serverInfo = ServerInfo(
            id = config.serverId,
            name = config.serverName,
            type = serverType,
            host = config.host,
            port = config.port,
            maxPlayers = config.maxPlayers
        )

        heartbeatTtl = config.heartbeatTimeoutSeconds
        tokenSigner = TransferTokenSigner(config.transferCookieSecret)
        transferCookieKey = NamespacedKey(NexusPlugin, "transfer_token")

        this.register()

        // Attempt immediate bring-up with the existing controller.
        val currentRedis = NexusPlugin.redis
        if (currentRedis != null) {
            watchRedisConnection(currentRedis)
            scheduler.launch { goOnline(currentRedis) }
        } else {
            NexusPlugin.logger.info(
                "[Servers] Redis unavailable at startup – network state: DEGRADED. " +
                "Retrying every ${config.redisReconnectIntervalSeconds}s."
            )
            initialConnectJob = scheduler.launch(Dispatchers.Default) {
                initialConnectLoop(config.redisReconnectIntervalSeconds)
            }
        }
    }

    override fun onDisposed() {
        this.unregister()

        initialConnectJob?.cancel()
        initialConnectJob = null
        redisConnectionEventsJob?.cancel()
        redisConnectionEventsJob = null
        pendingOnlineTransitionJob?.cancel()
        pendingOnlineTransitionJob = null
        pendingDegradedTransitionJob?.cancel()
        pendingDegradedTransitionJob = null

        if (networkState == NetworkState.ONLINE) {
            runBlocking { shutdownNetwork() }
        }

        goDegraded()
    }

    // ── Network lifecycle ──────────────────────────────────────────────────────

    /**
     * Transitions the feature to [NetworkState.ONLINE] using the provided [redis] connection.
     * Initialises registries, registers this server, resyncs currently-online players,
     * and starts the heartbeat.
     */
    private suspend fun goOnline(redis: RedisController) {
        if (!networkTransitionInProgress.compareAndSet(false, true)) return
        try {
            if (networkState == NetworkState.ONLINE) return

            NexusPlugin.logger.info("[Servers] Connecting to Redis...")

            cancelRegistryEventJobs()

            try {
                RedisConfig.enableKeyspaceNotifications(redis)
                RedisConfig.validateConfiguration(redis)
            } catch (e: Exception) {
                NexusPlugin.logger.warning("[Servers] Failed to configure Redis: ${e.message}")
            }

            val newServerRegistry = RedisServerRegistry(redis, scheduler)
            val newPlayerRegistry = RedisPlayerRegistry(redis, scheduler)

            serverRegistry = newServerRegistry
            playerRegistry = newPlayerRegistry

            // Register this server
            try {
                newServerRegistry.registerServer(serverInfo)
                NexusPlugin.logger.info("[Servers] Registered server '${serverInfo.name}' (${serverInfo.id}) as online")
            } catch (e: Exception) {
                NexusPlugin.logger.severe("[Servers] Failed to register server: ${e.message}")
            }

            // Resync currently-online players into Redis
            for (player in NexusPlugin.server.onlinePlayers) {
                try {
                    newPlayerRegistry.registerPlayer(player.uniqueId, player.name, serverInfo.id, heartbeatTtl)
                } catch (e: Exception) {
                    NexusPlugin.logger.warning("[Servers] Failed to resync player ${player.name}: ${e.message}")
                }
            }

            // Subscribe to registry events
            registryEventJobs += scheduler.launch {
                newServerRegistry.serverOnlineEvents.collect { event ->
                    if (event.server.id != serverInfo.id) {
                        NexusPlugin.logger.info("[Servers] Server '${event.server.name}' (${event.server.id}) came online")
                    }
                }
            }

            registryEventJobs += scheduler.launch {
                newServerRegistry.serverOfflineEvents.collect { event ->
                    if (event.serverId != serverInfo.id) {
                        NexusPlugin.logger.info("[Servers] Server '${event.serverId}' went offline")
                        try {
                            newPlayerRegistry.cleanupServerPlayers(event.serverId)
                        } catch (e: Exception) {
                            NexusPlugin.logger.warning("[Servers] Failed to cleanup players from ${event.serverId}: ${e.message}")
                        }
                    }
                }
            }

            registryEventJobs += scheduler.launch {
                newPlayerRegistry.playerOfflineEvents.collect { event ->
                    NexusPlugin.logger.info("[Servers] Player ${event.playerId} went offline from ${event.lastServerId}")
                }
            }

            registryEventJobs += scheduler.launch {
                newPlayerRegistry.playerOnlineEvents.collect { event ->
                    NexusPlugin.logger.info("[Servers] Player ${event.player.username} came online on ${event.player.serverId}")
                }
            }

            registryEventJobs += scheduler.launch {
                newPlayerRegistry.playerChangeServerEvents.collect { event ->
                    NexusPlugin.logger.info("[Servers] Player ${event.playerId} transferred from ${event.fromServerId} to ${event.toServerId}")
                }
            }

            networkState = NetworkState.ONLINE

            // Start heartbeat
            val config = NexusPlugin.configuration.features.servers
            startHeartbeat(config.heartbeatIntervalSeconds, config.heartbeatTimeoutSeconds)

            // Bring up global chat
            if (globalChat == null) {
                globalChat = GlobalChatHandler(this, redis)
            }

            NexusPlugin.logger.info("[Servers] Network state: ONLINE")
        } finally {
            networkTransitionInProgress.set(false)
        }
    }

    /**
     * Transitions the feature to [NetworkState.DEGRADED].
     * Stops the heartbeat and marks network features as unavailable.
     * Does **not** unregister from Redis (the TTL will expire naturally).
     */
    private fun goDegraded() {
        if (!networkTransitionInProgress.compareAndSet(false, true)) return
        try {
            if (networkState == NetworkState.DEGRADED) return
            networkState = NetworkState.DEGRADED

            heartbeatJob?.cancel()
            heartbeatJob = null
            cancelRegistryEventJobs()

            serverRegistry = null
            playerRegistry = null

            globalChat?.dispose()
            globalChat = null

            NexusPlugin.logger.warning("[Servers] Network state: DEGRADED (Redis unreachable). Transfers disabled.")
        } finally {
            networkTransitionInProgress.set(false)
        }
    }

    /**
     * Cleanly shuts down the network – unregisters server and players from Redis.
     * Only called on full feature disable when [NetworkState.ONLINE].
     */
    private suspend fun shutdownNetwork() {
        heartbeatJob?.cancel()
        heartbeatJob = null

        val registry = serverRegistry ?: return
        val playerReg = playerRegistry ?: return

        try {
            playerReg.cleanupServerPlayers(serverInfo.id)
            registry.unregisterServer(serverInfo.id)
            NexusPlugin.logger.info("[Servers] Unregistered server '${serverInfo.name}' (${serverInfo.id})")
        } catch (e: Exception) {
            NexusPlugin.logger.severe("[Servers] Failed to unregister server: ${e.message}")
        }
    }

    /**
     * Retry loop used only until the first Redis controller is established.
     * Runtime disconnect/reconnect is handled by Lettuce auto-reconnect + event bus.
     */
    private suspend fun initialConnectLoop(reconnectIntervalSeconds: Long) = coroutineScope {
        val conf = NexusPlugin.configuration.redis
        while (isActive && NexusPlugin.redis == null) {
            val redis = try {
                RedisController(
                    host = conf.host,
                    port = conf.port,
                    password = conf.password
                )
            } catch (_: Exception) {
                delay(reconnectIntervalSeconds.seconds)
                continue
            }

            NexusPlugin.redis = redis
            NexusPlugin.profiles.connectRedis(redis)
            watchRedisConnection(redis)
            goOnline(redis)
            return@coroutineScope
        }
    }

    private fun watchRedisConnection(redis: RedisController) {
        redisConnectionEventsJob?.cancel()
        redisConnectionEventsJob = scheduler.launch(Dispatchers.Default) {
            while (isActive) {
                try {
                    redis.connectionStates().collect { state ->
                        when (state) {
                            RedisController.ConnectionState.CONNECTED -> {
                                pendingDegradedTransitionJob?.cancel()
                                pendingDegradedTransitionJob = null

                                pendingOnlineTransitionJob?.cancel()
                                pendingOnlineTransitionJob = scheduler.launch(Dispatchers.Default) {
                                    try {
                                        delay(CONNECTION_STATE_STABILIZATION_DELAY)
                                        if (redis.isReachable()) {
                                            goOnline(redis)
                                        }
                                    } catch (_: CancellationException) {
                                        return@launch
                                    }
                                }
                            }

                            RedisController.ConnectionState.DISCONNECTED -> {
                                pendingOnlineTransitionJob?.cancel()
                                pendingOnlineTransitionJob = null

                                pendingDegradedTransitionJob?.cancel()
                                pendingDegradedTransitionJob = scheduler.launch(Dispatchers.Default) {
                                    try {
                                        delay(CONNECTION_STATE_STABILIZATION_DELAY)
                                        if (!redis.isReachable()) {
                                            goDegraded()
                                        }
                                    } catch (_: CancellationException) {
                                        return@launch
                                    }
                                }
                            }
                        }
                    }
                    break
                } catch (e: Exception) {
                    NexusPlugin.logger.warning("[Servers] Redis connection event stream failed: ${e.message}")
                    delay(1.seconds)
                }
            }
        }
    }

    private fun cancelRegistryEventJobs() {
        registryEventJobs.forEach { it.cancel() }
        registryEventJobs.clear()
    }

    // ── Heartbeat ─────────────────────────────────────────────────────────────

    private fun startHeartbeat(intervalSeconds: Long, ttl: Long) {
        heartbeatJob?.cancel()
        heartbeatJob = scheduler.launch(Dispatchers.Default) {
            while (isActive) {
                delay(intervalSeconds.seconds)
                val reg = serverRegistry ?: break
                val playerReg = playerRegistry ?: break
                try {
                    val playerCount = NexusPlugin.server.onlinePlayers.size
                    reg.updateHeartbeat(serverInfo.id, playerCount, ttl)
                    val onlineIds = NexusPlugin.server.onlinePlayers.map { it.uniqueId }.toSet()
                    playerReg.refreshServerPlayersTtl(serverInfo.id, ttl, onlineIds)
                } catch (e: Exception) {
                    NexusPlugin.logger.warning("[Servers] Heartbeat failed: ${e.message}. Transitioning to DEGRADED.")
                    goDegraded()
                    break
                }
            }
        }
    }

    // ── Bukkit event handlers ─────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val reg = playerRegistry ?: return
        scheduler.launch {
            try {
                reg.registerPlayer(
                    playerId = event.player.uniqueId,
                    username = event.player.name,
                    serverId = serverInfo.id,
                    ttl = heartbeatTtl
                )
            } catch (e: Exception) {
                NexusPlugin.logger.warning("[Servers] Failed to track player join: ${e.message}")
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    fun onConnectionValidate(event: PlayerConnectionValidateLoginEvent) {
        val conn = event.connection

        // DEGRADED – skip all network enforcement; local-only mode
        if (networkState == NetworkState.DEGRADED) return

        val reg = playerRegistry ?: return

        if (conn is PlayerLoginConnection) {
            // First stage: profile lookup. Check for duplicate sessions.
            runBlocking {
                val id = conn.authenticatedProfile?.id ?: run {
                    event.kickMessage(text("Failed to verify your profile, please try again later", RED))
                    return@runBlocking
                }

                val existingPlayer = try {
                    reg.getPlayer(id)
                } catch (_: Exception) {
                    return@runBlocking // Redis error – allow rather than blocking
                }

                if (existingPlayer != null
                    && existingPlayer.status != PlayerStatus.TRANSFERRING
                    && existingPlayer.serverId != null
                    && existingPlayer.serverId != serverInfo.id
                ) {
                    event.kickMessage(translatable("multiserver.kick.already_online", RED))
                    return@runBlocking
                }
                else if (existingPlayer == null
                    || existingPlayer.serverId == serverInfo.id
                    || existingPlayer.status != PlayerStatus.TRANSFERRING
                ) {
                    return@runBlocking
                }

                // Retrieve the transfer cookie asynchronously then validate synchronously.
                val cookieBytes: ByteArray? = try {
                    conn.retrieveCookie(transferCookieKey).await()
                } catch (_: Exception) {
                    null
                }

                val token = tokenSigner.decode(
                    cookieBytes = cookieBytes,
                    expectedPlayerId = id.toString(),
                    expectedToServerId = serverInfo.id
                )

                if (token == null) {
                    event.kickMessage(
                        text("Transfer validation failed: missing or invalid transfer token. Please try again.", RED)
                    )
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val reg = playerRegistry ?: return
        scheduler.launch {
            try {
                // Don't remove transferring players – the target or TTL will handle them.
                val player = reg.getPlayer(event.player.uniqueId)
                if (player != null && player.status == PlayerStatus.TRANSFERRING) return@launch
                reg.removePlayer(event.player.uniqueId)
            } catch (e: Exception) {
                NexusPlugin.logger.warning("[Servers] Failed to track player quit: ${e.message}")
            }
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Transfer a player to the given server.
     *
     * Returns `false` (without transferring) when:
     * - The network state is [NetworkState.DEGRADED].
     * - The target server is not found or is offline.
     *
     * When successful, a signed [TransferToken] cookie is stored on the player before
     * handing the connection off to the target via [Player.transfer].
     *
     * @param player The player to transfer.
     * @param serverId The ID of the target server.
     * @return `true` if the transfer was initiated, `false` otherwise.
     */
    suspend fun transferPlayer(player: Player, serverId: String): Boolean {
        if (networkState == NetworkState.DEGRADED) return false

        val reg = serverRegistry ?: return false
        val playerReg = playerRegistry ?: return false

        val targetServer = reg.getServer(serverId)
        if (targetServer == null || !targetServer.online) return false

        // Mark transferring BEFORE storing the cookie so the quit handler doesn't remove the entry.
        playerReg.markTransferring(player.uniqueId, heartbeatTtl)

        // Build and store the signed transfer token as a cookie.
        val now = System.currentTimeMillis()
        val tokenTtlMs = NexusPlugin.configuration.features.servers.tokenTtlSeconds * 1000L
        val token = TransferToken(
            transferId = UUID.randomUUID().toString(),
            playerId = player.uniqueId.toString(),
            fromServerId = serverInfo.id,
            toServerId = targetServer.id,
            issuedAtMillis = now,
            expiresAtMillis = now + tokenTtlMs
        )
        val cookieBytes = tokenSigner.encode(token)
        player.storeCookie(transferCookieKey, cookieBytes)

        player.transfer(targetServer.host, targetServer.port)
        return true
    }

    /**
     * Get all online servers (requires [NetworkState.ONLINE]).
     *
     * @return Collection of online servers, or empty if in [NetworkState.DEGRADED] state.
     */
    suspend fun getOnlineServers(): Collection<ServerInfo> {
        return serverRegistry?.getOnlineServers() ?: emptyList()
    }

    /**
     * Get the server a player is currently on.
     *
     * @param playerId The UUID of the player.
     * @return The server info, or null if not found or state is [NetworkState.DEGRADED].
     */
    suspend fun getPlayerServer(playerId: UUID): ServerInfo? {
        val reg = playerRegistry ?: return null
        val player = reg.getPlayer(playerId) ?: return null
        val sId = player.serverId ?: return null
        return serverRegistry?.getServer(sId)
    }
}
