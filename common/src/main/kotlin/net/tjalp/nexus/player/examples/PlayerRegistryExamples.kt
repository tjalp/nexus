@file:OptIn(ExperimentalTime::class)

package net.tjalp.nexus.player.examples

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.tjalp.nexus.player.PlayerRegistry
import net.tjalp.nexus.server.ServerRegistry
import java.util.*
import kotlin.time.ExperimentalTime

/**
 * Example usage patterns for the PlayerRegistry system
 */
class PlayerRegistryExamples(
    private val playerRegistry: PlayerRegistry,
    private val serverRegistry: ServerRegistry,
    private val scope: CoroutineScope
) {

    /**
     * Example: Check if a player is online and where they are
     */
    suspend fun findPlayer(playerId: UUID) {
        val player = playerRegistry.getPlayer(playerId)

        if (player == null) {
            println("Player is offline or not found")
            return
        }

        println("Player ${player.username} is online")

        if (player.serverId != null) {
            val server = serverRegistry.getServer(player.serverId)
            if (server != null) {
                println("They are on server: ${server.name} (${server.type})")
            }
        }
    }

    /**
     * Example: List all players on a specific server
     */
    suspend fun listPlayersOnServer(serverId: String) {
        val server = serverRegistry.getServer(serverId)

        if (server == null) {
            println("Server not found or offline")
            return
        }

        val players = playerRegistry.getPlayersByServer(serverId)

        println("Server: ${server.name}")
        println("Players online: ${players.size}")

        players.forEach { player ->
            println("  - ${player.username} (last seen: ${player.lastSeen})")
        }
    }

    /**
     * Example: Get network-wide statistics
     */
    suspend fun getNetworkStats() {
        val servers = serverRegistry.getOnlineServers()
        val players = playerRegistry.getOnlinePlayers()

        println("=== Network Statistics ===")
        println("Servers online: ${servers.size}")
        println("Players online: ${players.size}")

        // Players per server
        servers.forEach { server ->
            val serverPlayers = playerRegistry.getPlayersByServer(server.id)
            println("${server.name}: ${serverPlayers.size}/${server.maxPlayers} players")
        }
    }

    /**
     * Example: Subscribe to player events for logging
     */
    fun setupEventLogging() {
        // Log when players come online
        scope.launch {
            playerRegistry.playerOnlineEvents.collect { event ->
                val server = serverRegistry.getServer(event.player.serverId ?: return@collect)
                println("[JOIN] ${event.player.username} joined ${server?.name}")
            }
        }

        // Log when players go offline
        scope.launch {
            playerRegistry.playerOfflineEvents.collect { event ->
                println("[QUIT] Player ${event.playerId} left ${event.lastServerId}")
            }
        }

        // Log when players change servers
        scope.launch {
            playerRegistry.playerChangeServerEvents.collect { event ->
                val fromServer = event.fromServerId?.let { serverRegistry.getServer(it)?.name } ?: "unknown"
                val toServer = event.toServerId?.let { serverRegistry.getServer(it)?.name } ?: "unknown"
                println("[TRANSFER] Player ${event.playerId} moved from $fromServer to $toServer")
            }
        }
    }

    /**
     * Example: Find online friends
     */
    suspend fun findOnlineFriends(playerId: UUID, friendIds: List<UUID>): List<String> {
        val onlineFriends = mutableListOf<String>()

        friendIds.forEach { friendId ->
            val friend = playerRegistry.getPlayer(friendId)
            if (friend != null && friend.serverId != null) {
                val server = serverRegistry.getServer(friend.serverId)
                onlineFriends.add("${friend.username} on ${server?.name ?: "unknown"}")
            }
        }

        return onlineFriends
    }

    /**
     * Example: Check if a server has room for more players
     */
    suspend fun hasServerCapacity(serverId: String): Boolean {
        val server = serverRegistry.getServer(serverId) ?: return false

        if (server.maxPlayers < 0) {
            return true // Unlimited
        }

        val currentPlayers = playerRegistry.getPlayersByServer(serverId).size
        return currentPlayers < server.maxPlayers
    }

    /**
     * Example: Get the least populated server of a type
     */
    suspend fun findLeastPopulatedServer(serverType: net.tjalp.nexus.server.ServerType): String? {
        val servers = serverRegistry.getServersByType(serverType)

        return servers
            .filter { it.online }
            .map { server ->
                val playerCount = playerRegistry.getPlayersByServer(server.id).size
                server.id to playerCount
            }
            .minByOrNull { it.second }
            ?.first
    }

    /**
     * Example: Monitor server health and cleanup crashes
     */
    fun monitorServerHealth() {
        scope.launch {
            serverRegistry.serverOfflineEvents.collect { event ->
                println("[ALERT] Server ${event.serverId} went offline!")

                // Cleanup players from crashed server
                try {
                    playerRegistry.cleanupServerPlayers(event.serverId)
                    println("[CLEANUP] Removed players from ${event.serverId}")
                } catch (e: Exception) {
                    println("[ERROR] Failed to cleanup server ${event.serverId}: ${e.message}")
                }
            }
        }
    }
}

