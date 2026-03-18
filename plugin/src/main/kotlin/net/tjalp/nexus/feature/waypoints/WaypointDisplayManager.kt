package net.tjalp.nexus.feature.waypoints

import net.minecraft.core.Vec3i
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientboundTrackedWaypointPacket
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.world.waypoints.WaypointStyleAssets
import net.tjalp.nexus.NexusPlugin
import net.tjalp.nexus.util.sendPacket
import org.bukkit.entity.Player
import java.nio.charset.StandardCharsets
import java.util.*

/**
 * Runtime display manager for waypoints.
 *
 * This is the only layer that deals with packet-backed display mechanics.
 * The storage model stays packet/entity-agnostic.
 */
class WaypointDisplayManager {

    private val waypointIdsByKey = mutableMapOf<String, UUID>()
    private val trackedByPlayer = mutableMapOf<UUID, MutableSet<UUID>>()

    fun register(waypoint: Waypoint) {
        waypointIdsByKey.getOrPut(key(waypoint)) { trackingId(waypoint) }
        refresh(waypoint)
    }

    fun unregister(waypoint: Waypoint) {
        val waypointId = waypointIdsByKey.remove(key(waypoint)) ?: return

        for (player in NexusPlugin.server.onlinePlayers) {
            val tracked = trackedByPlayer[player.uniqueId] ?: continue
            if (tracked.remove(waypointId)) {
                player.sendPacket(buildUntrackPacket(waypointId))
            }
        }
    }

    fun refresh(waypoint: Waypoint) {
        val waypointId = waypointIdsByKey.getOrPut(key(waypoint)) { trackingId(waypoint) }

        for (player in NexusPlugin.server.onlinePlayers) {
            syncForPlayer(player, waypoint, waypointId)
        }
    }

    fun refresh(player: Player, waypoints: Iterable<Waypoint>) {
        for (waypoint in waypoints) {
            val waypointId = waypointIdsByKey.getOrPut(key(waypoint)) { trackingId(waypoint) }
            syncForPlayer(player, waypoint, waypointId)
        }
    }

    fun untrackAll(player: Player) {
        trackedByPlayer.remove(player.uniqueId)
    }

    fun clear() {
        for (player in NexusPlugin.server.onlinePlayers) {
            val tracked = trackedByPlayer[player.uniqueId] ?: continue
            for (waypointId in tracked.toList()) {
                player.sendPacket(buildUntrackPacket(waypointId))
            }
        }

        trackedByPlayer.clear()
        waypointIdsByKey.clear()
    }

    private fun syncForPlayer(player: Player, waypoint: Waypoint, waypointId: UUID) {
        val tracked = trackedByPlayer.getOrPut(player.uniqueId) { mutableSetOf() }
        val shouldBeVisible = waypoint.isVisibleTo(player)
        val isTracked = waypointId in tracked

        when {
            shouldBeVisible && !isTracked -> {
                player.sendPacket(buildTrackPacket(waypointId, waypoint))
                tracked += waypointId
            }
            shouldBeVisible && isTracked -> {
                player.sendPacket(buildUpdatePacket(waypointId, waypoint))
            }
            !shouldBeVisible && isTracked -> {
                player.sendPacket(buildUntrackPacket(waypointId))
                tracked -= waypointId
            }
        }
    }

    private fun key(waypoint: Waypoint): String {
        return "${waypoint.worldUuid}/${waypoint.id}"
    }

    private fun trackingId(waypoint: Waypoint): UUID {
        val source = "${waypoint.worldUuid}/${waypoint.id}"
        return UUID.nameUUIDFromBytes(source.toByteArray(StandardCharsets.UTF_8))
    }

    @Suppress("UNCHECKED_CAST")
    private fun buildTrackPacket(waypointId: UUID, waypoint: Waypoint): Packet<*> {
        val icon = createIcon(waypoint)
        val position = createPosition(waypoint)

        return ClientboundTrackedWaypointPacket.addWaypointPosition(waypointId, icon, position)
    }

    @Suppress("UNCHECKED_CAST")
    private fun buildUpdatePacket(waypointId: UUID, waypoint: Waypoint): Packet<*> {
        val icon = createIcon(waypoint)
        val position = createPosition(waypoint)

        return ClientboundTrackedWaypointPacket.updateWaypointPosition(waypointId, icon, position)
    }

    @Suppress("UNCHECKED_CAST")
    private fun buildUntrackPacket(waypointId: UUID): Packet<*> {
        return ClientboundTrackedWaypointPacket.removeWaypoint(waypointId)
    }

    private fun createPosition(waypoint: Waypoint): Vec3i {
        val location = waypoint.location

        return Vec3i(location.blockX, location.blockY, location.blockZ)
    }

    private fun createIcon(waypoint: Waypoint): MinecraftWaypointIcon {
        val key = ResourceKey.create(WaypointStyleAssets.ROOT_ID, Identifier.parse(waypoint.style.asString()))
        val icon = MinecraftWaypointIcon()

        icon.color = Optional.of(waypoint.color.asRGB())
        icon.style = key

        return icon
    }
}

typealias MinecraftWaypointIcon = net.minecraft.world.waypoints.Waypoint.Icon
