package net.tjalp.nexus.player

import kotlinx.coroutines.flow.Flow
import java.util.*

/**
 * Registry for tracking players in the network.
 *
 * The single source of truth is the per-player key in Redis (`nexus:player:info:{uuid}`).
 * Per-server player sets are derived indices that are kept consistent with the source of truth.
 */
interface PlayerRegistry {

    /**
     * Get information about a player by their ID
     *
     * @param playerId The unique identifier of the player
     * @return The player info, or null if not found/offline
     */
    suspend fun getPlayer(playerId: UUID): PlayerInfo?

    /**
     * Get all currently online players across the network
     * (includes players with status ONLINE or TRANSFERRING)
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
     * Get the global player count
     *
     * @return Number of players currently online across the network
     */
    suspend fun getPlayerCount(): Int

    /**
     * Get the number of players on a specific server
     *
     * @param serverId The ID of the server
     * @return Number of players on that server
     */
    suspend fun getPlayerCountOnServer(serverId: String): Long

    /**
     * Register a player as online on a specific server.
     * Sets the player status to ONLINE.
     *
     * @param playerId The UUID of the player
     * @param username The current username of the player
     * @param serverId The ID of the server the player is on
     * @param ttl Time in seconds until the player entry expires (aligned with server heartbeat)
     */
    suspend fun registerPlayer(playerId: UUID, username: String, serverId: String, ttl: Long = 60)

    /**
     * Mark a player as transferring between servers.
     * The player remains in the registry but their status changes to TRANSFERRING.
     * The TTL is kept so that if the transfer fails, the entry will expire.
     *
     * @param playerId The UUID of the player
     * @param ttl Time in seconds until the player entry expires if the transfer fails
     */
    suspend fun markTransferring(playerId: UUID, ttl: Long = 30)

    /**
     * Remove a player from the registry (going offline).
     *
     * @param playerId The UUID of the player
     */
    suspend fun removePlayer(playerId: UUID)

    /**
     * Clean up all players associated with a specific server.
     * Used when a server crashes or goes offline.
     *
     * @param serverId The ID of the server
     */
    suspend fun cleanupServerPlayers(serverId: String)

    /**
     * Refresh the TTL of players that are currently online on a server.
     * Called during heartbeat to keep player entries alive.
     *
     * Only players whose UUID is present in [onlinePlayerIds] will have their TTL
     * refreshed. Players that are absent (e.g. transferring) are intentionally
     * skipped so that their TTL is not extended by this server's heartbeat.
     *
     * @param serverId The ID of the server
     * @param ttl Time in seconds for the new TTL
     * @param onlinePlayerIds UUIDs of players that are genuinely online on this server right now
     */
    suspend fun refreshServerPlayersTtl(serverId: String, ttl: Long, onlinePlayerIds: Set<UUID>)

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


