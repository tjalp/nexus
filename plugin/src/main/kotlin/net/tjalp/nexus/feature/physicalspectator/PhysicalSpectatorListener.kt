package net.tjalp.nexus.feature.physicalspectator

import com.destroystokyo.paper.event.player.PlayerStartSpectatingEntityEvent
import io.papermc.paper.event.player.PlayerArmSwingEvent
import org.bukkit.GameMode
import org.bukkit.entity.Player
import org.bukkit.entity.Pose
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority.HIGH
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityPoseChangeEvent
import org.bukkit.event.player.*

class PhysicalSpectatorListener(
    private val feature: PhysicalSpectatorFeature
) : Listener {

    @EventHandler
    fun on(event: PlayerGameModeChangeEvent) {
        if (event.newGameMode == GameMode.SPECTATOR) return

        feature.removePhysicalBody(event.player)
    }

    @EventHandler(priority = HIGH, ignoreCancelled = true)
    fun on(event: PlayerMoveEvent) {
        feature.getPhysicalBody(event.player)?.teleport(event.to)
    }

    @EventHandler(priority = HIGH, ignoreCancelled = true)
    fun on(event: PlayerTeleportEvent) {
        feature.getPhysicalBody(event.player)?.teleport(event.to)
    }

    @EventHandler(priority = HIGH, ignoreCancelled = true)
    fun on(event: PlayerArmSwingEvent) {
        feature.getPhysicalBody(event.player)?.swingHand(event.hand)
    }

    @EventHandler(priority = HIGH, ignoreCancelled = true)
    fun on(event: EntityPoseChangeEvent) {
        val player = event.entity as? Player ?: return

        feature.getPhysicalBody(player)?.pose = event.pose
    }

    @EventHandler(priority = HIGH, ignoreCancelled = true)
    fun on(event: PlayerToggleSneakEvent) {
        val body = feature.getPhysicalBody(event.player) ?: return

        body.isSneaking = event.isSneaking
        body.pose = if (event.isSneaking) Pose.SNEAKING else Pose.STANDING
    }

    @EventHandler
    fun on(event: PlayerQuitEvent) {
        feature.removePhysicalBody(event.player)
    }

    @EventHandler
    fun on(event: PlayerStartSpectatingEntityEvent) {
        val player = event.player
        val hasBody = feature.hasPhysicalBody(player)

        feature.removePhysicalBody(event.player)

        if (hasBody) event.player.playerProfile = event.player.playerProfile
    }
}