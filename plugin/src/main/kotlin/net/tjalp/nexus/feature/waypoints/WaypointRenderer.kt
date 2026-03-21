package net.tjalp.nexus.feature.waypoints

import net.minecraft.core.Vec3i
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientboundTrackedWaypointPacket
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.waypoints.WaypointStyleAssets
import net.tjalp.nexus.NexusPlugin
import net.tjalp.nexus.util.PacketManager
import org.bukkit.entity.Player
import java.nio.charset.StandardCharsets
import java.util.*

/**
 * Runtime display manager for waypoints.
 *
 * This is the only layer that deals with packet-backed display mechanics.
 * The storage model stays packet/entity-agnostic.
 */
class WaypointRenderer {

    private val waypointIdsByKey = mutableMapOf<String, UUID>()
    private val trackedByPlayer = mutableMapOf<UUID, MutableSet<UUID>>()
    private val trackedSignaturesByPlayer = mutableMapOf<UUID, MutableMap<UUID, WaypointRetrackSignature>>()

    fun register(waypoint: Waypoint) {
        waypointIdsByKey.getOrPut(key(waypoint)) { trackingId(waypoint) }
        refresh(waypoint)
    }

    fun unregister(waypoint: Waypoint) {
        val waypointId = waypointIdsByKey.remove(key(waypoint)) ?: return

        for (player in NexusPlugin.server.onlinePlayers) {
            val tracked = trackedByPlayer[player.uniqueId] ?: continue
            if (tracked.remove(waypointId)) {
                PacketManager.sendPacket(player, buildUntrackPacket(waypointId))
            }
            trackedSignaturesByPlayer[player.uniqueId]?.remove(waypointId)
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
        trackedSignaturesByPlayer.remove(player.uniqueId)
    }

    fun clear() {
        for (player in NexusPlugin.server.onlinePlayers) {
            val tracked = trackedByPlayer[player.uniqueId] ?: continue
            for (waypointId in tracked.toList()) {
                PacketManager.sendPacket(player, buildUntrackPacket(waypointId))
            }
        }

        trackedByPlayer.clear()
        trackedSignaturesByPlayer.clear()
        waypointIdsByKey.clear()
    }

    private fun syncForPlayer(player: Player, waypoint: Waypoint, waypointId: UUID) {
        val tracked = trackedByPlayer.getOrPut(player.uniqueId) { mutableSetOf() }
        val trackedSignatures = trackedSignaturesByPlayer.getOrPut(player.uniqueId) { mutableMapOf() }
        val shouldBeVisible = waypoint.isVisibleTo(player)
        val isTracked = waypointId in tracked
        val currentSignature = waypoint.retrackSignature()

        when {
            shouldBeVisible && !isTracked -> {
                PacketManager.sendPacket(player, buildTrackPacket(waypointId, waypoint))
                tracked += waypointId
                trackedSignatures[waypointId] = currentSignature
            }
            shouldBeVisible && isTracked -> {
                val previousSignature = trackedSignatures[waypointId]
                if (previousSignature != currentSignature) {
                    // Client cannot apply these changes via update packets, so retrack.
                    PacketManager.sendPacket(player, buildUntrackPacket(waypointId))
                    PacketManager.sendPacket(player, buildTrackPacket(waypointId, waypoint))
                } else {
                    PacketManager.sendPacket(player, buildUpdatePacket(waypointId, waypoint))
                }
                trackedSignatures[waypointId] = currentSignature
            }
            !shouldBeVisible && isTracked -> {
                PacketManager.sendPacket(player, buildUntrackPacket(waypointId))
                tracked -= waypointId
                trackedSignatures.remove(waypointId)
            }
        }
    }

    private fun Waypoint.retrackSignature(): WaypointRetrackSignature {
        val targetType = when (target) {
            is WaypointTarget.Block -> WaypointTargetType.Block
            is WaypointTarget.Chunk -> WaypointTargetType.Chunk
            is WaypointTarget.Azimuth -> WaypointTargetType.Azimuth
        }

        return WaypointRetrackSignature(
            targetType = targetType,
            colorRgb = color.asRGB(),
            style = style.asString()
        )
    }

    private enum class WaypointTargetType { Block, Chunk, Azimuth }

    private data class WaypointRetrackSignature(
        val targetType: WaypointTargetType,
        val colorRgb: Int,
        val style: String
    )

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

        return when (val target = waypoint.target) {
            is WaypointTarget.Block -> ClientboundTrackedWaypointPacket.addWaypointPosition(waypointId, icon, createPosition(target))
            is WaypointTarget.Chunk -> ClientboundTrackedWaypointPacket.addWaypointChunk(waypointId, icon, createChunkPosition(target))
            is WaypointTarget.Azimuth -> ClientboundTrackedWaypointPacket.addWaypointAzimuth(waypointId, icon, target.angle)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun buildUpdatePacket(waypointId: UUID, waypoint: Waypoint): Packet<*> {
        val icon = createIcon(waypoint)

        return when (val target = waypoint.target) {
            is WaypointTarget.Block -> ClientboundTrackedWaypointPacket.updateWaypointPosition(waypointId, icon, createPosition(target))
            is WaypointTarget.Chunk -> ClientboundTrackedWaypointPacket.updateWaypointChunk(waypointId, icon, createChunkPosition(target))
            is WaypointTarget.Azimuth -> ClientboundTrackedWaypointPacket.updateWaypointAzimuth(waypointId, icon, target.angle)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun buildUntrackPacket(waypointId: UUID): Packet<*> {
        return ClientboundTrackedWaypointPacket.removeWaypoint(waypointId)
    }

    private fun createPosition(target: WaypointTarget.Block): Vec3i {
        return Vec3i(target.x, target.y, target.z)
    }

    private fun createChunkPosition(target: WaypointTarget.Chunk): ChunkPos {
        return ChunkPos(target.chunkX, target.chunkZ)
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
