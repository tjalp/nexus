package net.tjalp.nexus.feature.parkour

import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent

/**
 * Listens for player movement and forwards node transition context (from/to)
 * so runtime can reason about entry and exit semantics.
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

        val fromNodes = runtime.getNodesAt(from.world.uid, from.blockX, from.blockY, from.blockZ)
        val toNodes = runtime.getNodesAt(worldId, x, y, z)

        runtime.onPlayerMoved(player, fromNodes, toNodes)
    }

    @EventHandler
    fun on(event: PlayerQuitEvent) {
        runtime.clearPlayerSession(event.player.uniqueId)
    }
}
