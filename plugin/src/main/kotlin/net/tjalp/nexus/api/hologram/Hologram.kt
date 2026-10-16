package net.tjalp.nexus.api.hologram

import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent
import net.tjalp.nexus.api.hologram.line.HologramLine
import net.tjalp.nexus.util.isRegistered
import net.tjalp.nexus.util.register
import net.tjalp.nexus.util.unregister
import org.bukkit.Location
import org.bukkit.entity.TextDisplay
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.world.ChunkLoadEvent
import org.bukkit.event.world.ChunkUnloadEvent
import org.spongepowered.configurate.reactive.Disposable

/**
 * Represents a hologram in the game world, which is a visual display that can be seen by players.
 * Holograms can be global (visible to all players) or local (visible only to specific players).
 *
 * @param location The location of the hologram in the game world.
 */
class Hologram(
    location: Location
): Disposable {

    private val listener: Listener = HologramListener()
    private var _location = location
    private var lines = mutableListOf<HologramLine>()

    /**
     * The location of this hologram.
     *
     * @return The location of this hologram
     */
    val location: Location
        get() = _location.clone()

    /**
     * The visibility of this hologram.
     */
    var visibility: HologramVisibility = HologramVisibility.Global
        private set

    /**
     * Moves the hologram to a new location in the game world.
     *
     * @param location The new location to move the hologram to.
     */
    fun move(location: Location) {
        _location = location
        render(isMove = true)
    }

    /**
     * Spawns the hologram, setting up necessary listeners and rendering it in the game world.
     * Fails silently if the chunk containing the hologram's location is not loaded.
     */
    fun spawn() {
        if (!listener.isRegistered()) listener.register()

        if (location.isChunkLoaded) render(isMove = false)
    }

    /**
     * Despawns the hologram, removing it from the game world.
     */
    fun despawn(isChunkUnload: Boolean = false) {
        lines.forEach { it.despawn(isChunkUnload) }
    }

    /**
     * Sets the visibility of this hologram, determining which players can see it.
     *
     * @param visibility The new visibility for this hologram.
     */
    fun setVisibility(visibility: HologramVisibility) {
        this.visibility = visibility

        lines.forEach { line ->
            line.setVisibility(visibility)
        }
    }

    /**
     * Adds a new line to the hologram, positioning it correctly based on existing lines.
     *
     * @param line The new line to add.
     * @param spawn Whether to spawn the line immediately after adding it.
     */
    fun addLine(line: HologramLine, spawn: Boolean = false) {
        val spawnPos = location.clone()
        lines.forEach {
            spawnPos.subtract(0.0, it.height, 0.0)
        }

        lines.add(line)
        if (spawn) line.spawn(spawnPos, visibility)
    }

    private fun render(isMove: Boolean) {
        val absoluteLoc = location.clone()

        lines.forEach {
            if (isMove) it.move(absoluteLoc) else it.spawn(absoluteLoc, visibility)

            absoluteLoc.subtract(0.0, it.height, 0.0)
        }
    }

    override fun dispose() {
        listener.unregister()
        lines.forEach { it.dispose() }
        lines.clear()
    }

    private inner class HologramListener : Listener {

        @EventHandler
        fun on(event: ChunkLoadEvent) {
            val chunk = event.chunk
            if (chunk.world.uid != location.world.uid) return

            if (chunk.x == location.x.toInt() shr 4 && chunk.z == location.z.toInt() shr 4) {
//                Bukkit.broadcastMessage("Chunk loaded, spawning hologram... ${this@Hologram}")
                spawn()
            }
        }

        @EventHandler
        fun on(event: ChunkUnloadEvent) {
            val chunk = event.chunk
            if (chunk.world.uid != location.world.uid) return

            if (chunk.x == location.x.toInt() shr 4 && chunk.z == location.z.toInt() shr 4) {
//                Bukkit.broadcastMessage("Chunk unloaded, despawning hologram... ${this@Hologram}")
                despawn(isChunkUnload = true) // todo fix holograms not despawning on chunk unload...?
            }
        }

        @EventHandler
        fun on(event: EntityRemoveFromWorldEvent) {
            val entity = event.entity

            if (entity !is TextDisplay) return

//            Bukkit.broadcast(text("Display ${entity.uniqueId} removed from world ${entity.world.name}: ").append(entity.text()))
        }
    }
}