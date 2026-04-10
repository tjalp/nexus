package net.tjalp.nexus.feature.parkour

import net.tjalp.nexus.NexusPlugin
import org.bukkit.Location
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent
import java.util.*

/**
 * Listens for player movement to detect parkour node region entries.
 * Uses a chunk-indexed lookup to avoid scanning all nodes on every move.
 */
class ParkourListener(private val runtime: ParkourRuntimeService) : Listener {

    /** Tracks the last block position per player to avoid redundant checks. */
    private val lastBlock = mutableMapOf<UUID, Triple<UUID, Int, Int>>() // worldId, blockX, blockZ

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun on(event: PlayerMoveEvent) {
        val player = event.player
        val from = event.from
        val to = event.to

        // Only process if the player moved to a different block
        if (from.blockX == to.blockX && from.blockY == to.blockY && from.blockZ == to.blockZ) return

        val worldId = to.world.uid
        val x = to.blockX
        val y = to.blockY
        val z = to.blockZ

        lastBlock[player.uniqueId] = Triple(worldId, x shr 4, z shr 4)

        val nodes = runtime.getNodesAt(worldId, x, y, z)
        nodes.forEach { (parkour, node) ->
            runtime.onNodeEntered(player, parkour, node)
        }
    }

    @EventHandler
    fun on(event: PlayerChangedWorldEvent) {
        // Reset last-block tracking so the first block in the new world triggers normally
        lastBlock.remove(event.player.uniqueId)
    }

    @EventHandler
    fun on(event: PlayerQuitEvent) {
        lastBlock.remove(event.player.uniqueId)
        runtime.clearPlayerSession(event.player.uniqueId)
    }
}
