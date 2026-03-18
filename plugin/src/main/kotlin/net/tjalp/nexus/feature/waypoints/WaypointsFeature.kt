package net.tjalp.nexus.feature.waypoints

import io.papermc.paper.event.player.PlayerClientLoadedWorldEvent
import net.tjalp.nexus.Feature
import net.tjalp.nexus.NexusPlugin
import net.tjalp.nexus.feature.FeatureKeys.WAYPOINTS
import net.tjalp.nexus.util.register
import net.tjalp.nexus.util.unregister
import org.bukkit.NamespacedKey
import org.bukkit.World
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.world.WorldLoadEvent
import org.bukkit.event.world.WorldUnloadEvent
import org.bukkit.persistence.PersistentDataType

/**
 * Feature that manages waypoints in the world.
 */
class WaypointsFeature : Feature(WAYPOINTS), Listener {

    private val displayManager = WaypointDisplayManager()

    /**
     * All available waypoints across all (loaded) worlds.
     */
    val availableWaypoints: Collection<Waypoint>
        get() = NexusPlugin.server.worlds.flatMap { readWaypoints(it) }

    private val _loadedWaypoints = mutableSetOf<Waypoint>()

    val loadedWaypoints: Collection<Waypoint>
        get() = _loadedWaypoints.toSet()

    override fun onEnable() {
        this.register()

        // Handle worlds that are already loaded when the feature is enabled.
        NexusPlugin.server.worlds.forEach { world ->
            loadWaypoints(world)
        }
    }

    override fun onDisposed() {
        _loadedWaypoints.clear()
        displayManager.clear()
        this.unregister()
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
        val oldWaypoints = _loadedWaypoints.filter { it.world == world }
        oldWaypoints.forEach { displayManager.unregister(it) }
        _loadedWaypoints.removeAll(oldWaypoints.toSet())

        val waypoints = readWaypoints(world)
        _loadedWaypoints.addAll(waypoints)

        waypoints.forEach { displayManager.register(it) }
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
        displayManager.register(waypoint)
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
        displayManager.unregister(waypoint)
    }

    @EventHandler
    fun on(event: WorldLoadEvent) {
        loadWaypoints(event.world)
    }

    @EventHandler
    fun on(event: WorldUnloadEvent) {
        for (waypoint in loadedWaypoints) {
            if (waypoint.world == event.world) {
                displayManager.unregister(waypoint)
                _loadedWaypoints.remove(waypoint)
            }
        }
    }

    @EventHandler
    fun on(event: PlayerClientLoadedWorldEvent) {
        displayManager.refresh(event.player, loadedWaypoints)
    }

    @EventHandler
    fun on(event: PlayerChangedWorldEvent) {
        displayManager.refresh(event.player, loadedWaypoints)
    }

    @EventHandler
    fun on(event: PlayerQuitEvent) {
        displayManager.untrackAll(event.player)
    }

    companion object {

        /**
         * The NamespacedKey used to store waypoints in the world's persistent data container.
         */
        val WAYPOINTS_KEY = NamespacedKey(NexusPlugin, "waypoints")
    }
}