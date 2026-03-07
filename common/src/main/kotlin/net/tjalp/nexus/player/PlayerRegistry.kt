package net.tjalp.nexus.player

import kotlinx.coroutines.flow.Flow
import java.util.*

/**
 * Registry for tracking players in the network
 */
interface PlayerRegistry {

    /**
     * Get information about a player by their ID
     *
     * @param playerId The unique identifier of the player
     * @return The player info, or null if not found
     */
    suspend fun getPlayer(playerId: UUID): PlayerInfo?

    /**
     * Get all currently online players across the network
     *
     * @return Collection of all online players
     */
    suspend fun getOnlinePlayers(): Collection<PlayerInfo>

    /**
     * Get all players on a specific server
     *
     * @param serverId The ID of the server
     * @return Collection of players on that server
     */
    suspend fun getPlayersByServer(serverId: String): Collection<PlayerInfo>

    /**
     * Get the number of players on a specific server (more efficient than getPlayersByServer().size)
     *
     * @param serverId The ID of the server
     * @return Number of players on that server
     */
    suspend fun getPlayerCountOnServer(serverId: String): Long

    /**
     * Get all players in the registry (including offline players with recent activity)
     * Note: This may be a slow operation for large networks
     *
     * @return Collection of all players
     */
    suspend fun getAllPlayers(): Collection<PlayerInfo>

    /**
     * Update a player's location in the network
     *
     * @param playerId The UUID of the player
     * @param username The current username of the player
     * @param serverId The ID of the server the player is on, or null if going offline
     * @param ttl Time in seconds until the player entry expires (aligned with server heartbeat)
     */
    suspend fun updatePlayerLocation(playerId: UUID, username: String, serverId: String?, ttl: Long = 60)

    /**
     * Remove a player from the registry (cleanup)
     *
     * @param playerId The UUID of the player
     */
    suspend fun removePlayer(playerId: UUID)

    /**
     * Clean up all players associated with a specific server
     * Useful when a server crashes or goes offline
     *
     * @param serverId The ID of the server
     */
    suspend fun cleanupServerPlayers(serverId: String)

    /**
     * Flow of player online events
     */
    val playerOnlineEvents: Flow<PlayerOnlineEvent>

    /**
     * Flow of player offline events
     */
    val playerOfflineEvents: Flow<PlayerOfflineEvent>

    /**
     * Flow of player server change events
     */
    val playerChangeServerEvents: Flow<PlayerChangeServerEvent>
}


