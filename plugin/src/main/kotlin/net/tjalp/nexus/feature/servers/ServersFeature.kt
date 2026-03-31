package net.tjalp.nexus.feature.servers

import io.papermc.paper.connection.PlayerLoginConnection
import io.papermc.paper.event.connection.PlayerConnectionValidateLoginEvent
import kotlinx.coroutines.*
import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.Component.translatable
import net.kyori.adventure.text.format.NamedTextColor.RED
import net.kyori.adventure.text.format.NamedTextColor.GOLD
import net.tjalp.nexus.Feature
import net.tjalp.nexus.NexusPlugin
import net.tjalp.nexus.feature.FeatureKeys.SERVERS
import net.tjalp.nexus.player.PlayerRegistry
import net.tjalp.nexus.player.PlayerStatus
import net.tjalp.nexus.player.P2PPlayerRegistry
import net.tjalp.nexus.server.P2PServerRegistry
import net.tjalp.nexus.server.ServerInfo
import net.tjalp.nexus.server.ServerRegistry
import net.tjalp.nexus.server.ServerType
import net.tjalp.nexus.util.register
import net.tjalp.nexus.util.unregister
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import java.util.*
import kotlin.time.Duration.Companion.seconds

/**
 * Feature for managing multiserver infrastructure with P2P networking
 */
class ServersFeature : Feature(SERVERS), Listener {

    lateinit var serverRegistry: ServerRegistry
        private set

    lateinit var playerRegistry: PlayerRegistry
        private set

    lateinit var serverInfo: ServerInfo
        private set

    private var heartbeatJob: Job? = null
    private var heartbeatTtl: Long = 60

    var globalChat: GlobalChatHandler? = null
        private set

    var p2pApiServer: P2PApiServer? = null
        internal set

    override fun onEnable() {
        val config = NexusPlugin.configuration.features.servers

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

        NexusPlugin.logger.info("Initializing P2P mode for server networking...")
        initializeP2PMode(config)

        startHeartbeat(config.heartbeatIntervalSeconds, config.heartbeatTimeoutSeconds)

        this.register()

        // Subscribe to server events for logging
        scheduler.launch {
            serverRegistry.serverOnlineEvents.collect { event ->
                if (event.server.id != serverInfo.id) {
                    NexusPlugin.logger.info("Server '${event.server.name}' (${event.server.id}) came online")
                }
            }
        }

        scheduler.launch {
            serverRegistry.serverOfflineEvents.collect { event ->
                if (event.serverId != serverInfo.id) {
                    NexusPlugin.logger.info("Server '${event.serverId}' went offline")

                    // Clean up players from offline server
                    try {
                        playerRegistry.cleanupServerPlayers(event.serverId)
                    } catch (e: Exception) {
                        NexusPlugin.logger.warning("Failed to cleanup players from server ${event.serverId}: ${e.message}")
                    }
                }
            }
        }

        scheduler.launch {
            playerRegistry.playerOfflineEvents.collect { event ->
                NexusPlugin.logger.info("Player with id ${event.playerId} went offline from server ${event.lastServerId}")
            }
        }

        scheduler.launch {
            playerRegistry.playerOnlineEvents.collect { event ->
                NexusPlugin.logger.info("Player ${event.player.username} came online on server ${event.player.serverId}")
            }
        }

        scheduler.launch {
            playerRegistry.playerChangeServerEvents.collect { event ->
                NexusPlugin.logger.info("Player ${event.playerId} transferred from server ${event.fromServerId} to ${event.toServerId}")
            }
        }

        globalChat = GlobalChatHandler(this)
    }

    private fun initializeP2PMode(config: net.tjalp.nexus.config.ServersConfig) {
        val p2pServerRegistry = P2PServerRegistry(
            localServer = serverInfo,
            scope = scheduler,
            apiPort = config.p2p.apiPort,
            staticServers = config.p2p.staticServers
        )
        serverRegistry = p2pServerRegistry

        val p2pPlayerRegistry = P2PPlayerRegistry(
            serverRegistry = p2pServerRegistry,
            localServerId = serverInfo.id,
            scope = scheduler
        )
        playerRegistry = p2pPlayerRegistry

        // Start P2P API server
        p2pApiServer = P2PApiServer(
            serverInfo = serverInfo,
            serverRegistry = p2pServerRegistry,
            playerRegistry = p2pPlayerRegistry,
            port = config.p2p.apiPort
        )
        p2pApiServer?.start()

        // Register this server
        scheduler.launch {
            try {
                serverRegistry.registerServer(serverInfo)
                NexusPlugin.logger.info("Registered server '${serverInfo.name}' (${serverInfo.id}) in P2P mode")
            } catch (e: Exception) {
                NexusPlugin.logger.severe("Failed to register server: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    override fun onDisposed() {
        globalChat?.dispose()
        this.unregister()

        heartbeatJob?.cancel()

        // Stop P2P API server if running
        p2pApiServer?.stop()

        // Clean up players and unregister this server
        runBlocking {
            try {
                // Clean up all players on this server first
                playerRegistry.cleanupServerPlayers(serverInfo.id)

                serverRegistry.unregisterServer(serverInfo.id)
                NexusPlugin.logger.info("Unregistered server '${serverInfo.name}' (${serverInfo.id})")
            } catch (e: Exception) {
                NexusPlugin.logger.severe("Failed to unregister server: ${e.message}")
                e.printStackTrace()
            }
        }

        // Dispose P2P resources
        (serverRegistry as? P2PServerRegistry)?.dispose()
        (playerRegistry as? P2PPlayerRegistry)?.dispose()
    }

    /**
     * Start sending heartbeats to Redis
     */
    private fun startHeartbeat(intervalSeconds: Long, ttl: Long) {
        heartbeatJob = scheduler.launch(Dispatchers.Default) {
            while (true) {
                delay(intervalSeconds.seconds)
                try {
                    val playerCount = NexusPlugin.server.onlinePlayers.size
                    serverRegistry.updateHeartbeat(serverInfo.id, playerCount, ttl)

                    // Update P2P API server player count
                    p2pApiServer?.updatePlayerCount(playerCount)

                    // Refresh TTLs only for players that are genuinely online right now
                    val onlineIds = NexusPlugin.server.onlinePlayers.map { it.uniqueId }.toSet()
                    playerRegistry.refreshServerPlayersTtl(serverInfo.id, ttl, onlineIds)
                } catch (e: Exception) {
                    NexusPlugin.logger.warning("Failed to send heartbeat: ${e.message}")
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerJoin(event: PlayerJoinEvent) {
        scheduler.launch {
            try {
                playerRegistry.registerPlayer(
                    playerId = event.player.uniqueId,
                    username = event.player.name,
                    serverId = serverInfo.id,
                    ttl = heartbeatTtl
                )
            } catch (e: Exception) {
                NexusPlugin.logger.warning("Failed to track player join: ${e.message}")
            }
        }
    }

    @Suppress("UnstableApiUsage")
    @EventHandler(priority = EventPriority.LOW)
    fun onConnectionValidate(event: PlayerConnectionValidateLoginEvent) {
        val conn = event.connection

        if (conn !is PlayerLoginConnection) return

        // disallow if already online on another server in the network
        runBlocking {
            val id = conn.authenticatedProfile?.id

            if (id == null) {
                event.kickMessage(text("Failed to verify your profile, please try again later", RED))
                return@runBlocking
            }

            val existingPlayer = playerRegistry.getPlayer(id)

            if (existingPlayer != null
                && existingPlayer.status != PlayerStatus.TRANSFERRING
                && existingPlayer.serverId != null
                && existingPlayer.serverId != serverInfo.id
            ) {
                event.kickMessage(translatable("multiserver.kick.already_online", RED))
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerQuit(event: PlayerQuitEvent) {
        scheduler.launch {
            try {
                // Check if the player is transferring – if so, don't remove them.
                // The receiving server will call registerPlayer() to update their info,
                // or the TTL will expire if the transfer fails.
                val player = playerRegistry.getPlayer(event.player.uniqueId)
                if (player != null && player.status == PlayerStatus.TRANSFERRING) {
                    return@launch
                }

                playerRegistry.removePlayer(event.player.uniqueId)
            } catch (e: Exception) {
                NexusPlugin.logger.warning("Failed to track player quit: ${e.message}")
            }
        }
    }

    /**
     * Transfer a player to another server with enhanced health checks.
     *
     * This method performs pre-transfer validation to ensure:
     * - The target server exists and is online
     * - The target server is healthy and responding
     * - The target server has capacity for the player
     *
     * If validation fails, the player is notified and remains on the current server.
     *
     * @param player The player to transfer
     * @param serverId The ID of the target server
     * @return True if transfer was initiated successfully, false otherwise
     */
    suspend fun transferPlayer(player: Player, serverId: String): Boolean {
        val targetServer = serverRegistry.getServer(serverId)

        if (targetServer == null) {
            player.sendActionBar(text("Target server is offline or not found", RED))
            return false
        }

        // Check server availability before transfer
        val availability = (serverRegistry as P2PServerRegistry).checkServerAvailability(serverId)

        if (!availability.available) {
            player.sendActionBar(text("Cannot transfer: ${availability.reason}", RED))
            NexusPlugin.logger.warning("Transfer blocked for ${player.name} to $serverId: ${availability.reason}")
            return false
        }

        NexusPlugin.logger.info("Server $serverId is available (${availability.playerCount} players)")

        // Mark the player as transferring BEFORE initiating the transfer.
        // This prevents onPlayerQuit from fully removing them from the registry.
        playerRegistry.markTransferring(player.uniqueId, heartbeatTtl)

        player.sendActionBar(text("Transferring to ${targetServer.name}...", GOLD))

        try {
            player.transfer(targetServer.host, targetServer.port)
            return true
        } catch (e: Exception) {
            // Transfer initiation failed
            NexusPlugin.logger.warning("Failed to initiate transfer for ${player.name}: ${e.message}")
            player.sendActionBar(text("Transfer failed: ${e.message}", RED))

            // Re-register player as online on current server since transfer failed
            playerRegistry.registerPlayer(
                playerId = player.uniqueId,
                username = player.name,
                serverId = serverInfo.id,
                ttl = heartbeatTtl
            )

            return false
        }
    }

    /**
     * Get all online servers
     *
     * @return Collection of all online servers
     */
    suspend fun getOnlineServers(): Collection<ServerInfo> {
        return serverRegistry.getOnlineServers()
    }

    /**
     * Get the server a player is currently on
     *
     * @param playerId The UUID of the player
     * @return The server info, or null if player is not online or server not found
     */
    suspend fun getPlayerServer(playerId: UUID): ServerInfo? {
        val player = playerRegistry.getPlayer(playerId) ?: return null
        val serverId = player.serverId ?: return null
        return serverRegistry.getServer(serverId)
    }
}

