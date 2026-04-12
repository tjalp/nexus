package net.tjalp.nexus.feature.parkour

import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent

/**
 * Listens for player movement to detect parkour node region entries.
 * Uses a chunk-indexed lookup to avoid scanning all nodes on every move.
 */
class ParkourListener(private val runtime: ParkourRuntimeService) : Listener {

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

        runtime.getNodesAt(worldId, x, y, z).forEach { node ->
            runtime.onNodeEntered(player, node)
        }
    }

    @EventHandler
    fun on(event: PlayerChangedWorldEvent) {
        // No per-player state to clean up; sessions are kept alive across world changes.
    }

    @EventHandler
    fun on(event: PlayerQuitEvent) {
        runtime.clearPlayerSession(event.player.uniqueId)
    }
}
