package net.tjalp.nexus.feature.physicalspectator

import io.papermc.paper.event.player.PlayerArmSwingEvent
import org.bukkit.GameMode
import org.bukkit.entity.Player
import org.bukkit.entity.Pose
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority.HIGH
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityPoseChangeEvent
import org.bukkit.event.player.PlayerGameModeChangeEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerTeleportEvent
import org.bukkit.event.player.PlayerToggleSneakEvent

class PhysicalSpectatorListener : Listener {

    @EventHandler
    fun on(event: PlayerGameModeChangeEvent) {
        if (event.newGameMode == GameMode.SPECTATOR) return

        PhysicalSpectatorFeature.removePhysicalBody(event.player)
    }

    @EventHandler(priority = HIGH, ignoreCancelled = true)
    fun on(event: PlayerMoveEvent) {
        PhysicalSpectatorFeature.getPhysicalBody(event.player)?.teleport(event.to)
    }

    @EventHandler(priority = HIGH, ignoreCancelled = true)
    fun on(event: PlayerTeleportEvent) {
        PhysicalSpectatorFeature.getPhysicalBody(event.player)?.teleport(event.to)
    }

    @EventHandler(priority = HIGH, ignoreCancelled = true)
    fun on(event: PlayerArmSwingEvent) {
        PhysicalSpectatorFeature.getPhysicalBody(event.player)?.swingHand(event.hand)
    }

    @EventHandler(priority = HIGH, ignoreCancelled = true)
    fun on(event: EntityPoseChangeEvent) {
        val player = event.entity as? Player ?: return

        PhysicalSpectatorFeature.getPhysicalBody(player)?.pose = event.pose
    }

    @EventHandler(priority = HIGH, ignoreCancelled = true)
    fun on(event: PlayerToggleSneakEvent) {
        val body = PhysicalSpectatorFeature.getPhysicalBody(event.player) ?: return

        body.isSneaking = event.isSneaking
        body.pose = if (event.isSneaking) Pose.SNEAKING else Pose.STANDING
    }
}