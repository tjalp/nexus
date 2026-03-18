package net.tjalp.nexus.feature.waypoints

import net.tjalp.nexus.NexusPlugin
import org.bukkit.attribute.Attribute
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player

/**
 * Runtime display manager for waypoints.
 *
 * This is the only layer that deals with entity-backed display mechanics.
 * The storage model stays packet/entity-agnostic.
 */
class WaypointDisplayManager {

    private val entitiesByWaypoint = mutableMapOf<String, LivingEntity>()

    fun register(waypoint: Waypoint) {
        val world = waypoint.world ?: return
        val key = key(waypoint)

        if (entitiesByWaypoint[key]?.isValid == true) {
            refresh(waypoint)
            return
        }

        val entity = world.spawn(waypoint.location, ArmorStand::class.java) {
            it.isMarker = true
            it.isPersistent = false
            it.isInvisible = true
            it.waypointColor = waypoint.color
            it.setWaypointStyle(waypoint.style)
            it.getAttribute(Attribute.WAYPOINT_TRANSMIT_RANGE)?.baseValue = waypoint.transmitRange
        }

        entitiesByWaypoint[key] = entity
        refresh(waypoint)
    }

    fun unregister(waypoint: Waypoint) {
        entitiesByWaypoint.remove(key(waypoint))?.remove()
    }

    fun refresh(waypoint: Waypoint) {
        val entity = entitiesByWaypoint[key(waypoint)] ?: return
        val world = waypoint.world ?: return

        for (player in world.players) {
            applyVisibility(waypoint, player, entity)
        }
    }

    fun refresh(player: Player, waypoints: Iterable<Waypoint>) {
        for (waypoint in waypoints) {
            val entity = entitiesByWaypoint[key(waypoint)] ?: continue
            applyVisibility(waypoint, player, entity)
        }
    }

    fun clear() {
        entitiesByWaypoint.values.forEach { it.remove() }
        entitiesByWaypoint.clear()
    }

    private fun applyVisibility(waypoint: Waypoint, player: Player, entity: LivingEntity) {
        if (waypoint.isVisibleTo(player)) {
            player.showEntity(NexusPlugin, entity)
        } else {
            player.hideEntity(NexusPlugin, entity)
        }
    }

    private fun key(waypoint: Waypoint): String {
        val worldId = waypoint.world?.uid?.toString() ?: "missing-world"
        return "$worldId/${waypoint.id}"
    }
}

