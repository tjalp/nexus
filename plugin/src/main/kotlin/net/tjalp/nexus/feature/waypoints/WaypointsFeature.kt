package net.tjalp.nexus.feature.waypoints

import net.tjalp.nexus.Feature
import net.tjalp.nexus.NexusPlugin
import net.tjalp.nexus.util.register
import net.tjalp.nexus.util.unregister
import org.bukkit.NamespacedKey
import org.bukkit.World
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.world.WorldLoadEvent
import org.bukkit.event.world.WorldUnloadEvent
import org.bukkit.persistence.PersistentDataType

/**
 * Feature that manages waypoints in the world.
 */
object WaypointsFeature : Feature("waypoints"), Listener {

    /**
     * The NamespacedKey used to store waypoints in the world's persistent data container.
     */
    val WAYPOINTS_KEY = NamespacedKey(NexusPlugin, "waypoints")

    /**
     * All available waypoints across all (loaded) worlds.
     */
    val availableWaypoints: Collection<Waypoint>
        get() = NexusPlugin.server.worlds.flatMap { readWaypoints(it) }

    private val _loadedWaypoints = mutableSetOf<Waypoint>()

    val loadedWaypoints: Collection<Waypoint>
        get() = _loadedWaypoints.toSet()

    override fun enable() {
        super.enable()

        this.register()
    }

    override fun disable() {
        this.unregister()

        super.disable()
    }

    /**
     * Reads all registered waypoints in the given world.
     *
     * @param world The world to get the waypoints from.
     * @return A collection of waypoints registered in the world.
     */
    fun readWaypoints(world: World): Collection<Waypoint> {
        return world.persistentDataContainer.getOrDefault(
            WAYPOINTS_KEY,
            PersistentDataType.LIST.listTypeFrom(WaypointDataType),
            emptyList()
        )
    }

    /**
     * Loads all waypoints in the given world, despawning any previously loaded waypoints,
     * and spawning the newly loaded ones.
     *
     * @param world The world to load the waypoints from.
     */
    fun loadWaypoints(world: World) {
        val waypoints = readWaypoints(world)

        _loadedWaypoints.forEach { it.despawn() }
        _loadedWaypoints.clear()
        _loadedWaypoints.addAll(waypoints)

        waypoints.forEach { it.spawn(world) }
    }

    /**
     * Saves a waypoint in the world's persistent data container,
     * but does not spawn it.
     *
     * @param world The world to register the waypoint in.
     * @param waypoint The waypoint to register.
     * @throws IllegalArgumentException if a waypoint with the same id is already registered in the world.
     */
    fun saveWaypoint(world: World, waypoint: Waypoint) {
        val waypoints = readWaypoints(world).filter { it.id != waypoint.id }

        world.persistentDataContainer.set(
            WAYPOINTS_KEY,
            PersistentDataType.LIST.listTypeFrom(WaypointDataType),
            waypoints.plus(waypoint)
        )

        _loadedWaypoints.add(waypoint)
    }

    /**
     * Removes a waypoint from the world's persistent data container,
     * and despawns it if it is spawned.
     *
     * @param world The world to unregister the waypoint from.
     * @param waypoint The waypoint to unregister.
     */
    fun removeWaypoint(world: World, waypoint: Waypoint) {
        val waypoints = readWaypoints(world).filter { it.id != waypoint.id }

        world.persistentDataContainer.set(
            WAYPOINTS_KEY,
            PersistentDataType.LIST.listTypeFrom(WaypointDataType),
            waypoints
        )

        _loadedWaypoints.remove(waypoint)
        waypoint.despawn()
    }

    @EventHandler
    fun on(event: WorldLoadEvent) {
        val world = event.world
        val waypoints = readWaypoints(world)

        _loadedWaypoints.addAll(waypoints)

        waypoints.forEach { it.spawn(world) }
    }

    @EventHandler
    fun on(event: WorldUnloadEvent) {
        for (waypoint in loadedWaypoints.toList()) {
            if (waypoint.location.world == event.world) {
                waypoint.despawn()
                _loadedWaypoints.remove(waypoint)
            }
        }
    }
}