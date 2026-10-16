package net.tjalp.nexus.api.hologram.line

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.ComponentLike
import net.tjalp.nexus.NexusPlugin
import net.tjalp.nexus.api.hologram.HologramVisibility
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Display
import org.bukkit.entity.TextDisplay
import org.bukkit.event.entity.CreatureSpawnEvent
import java.util.*

class ComponentLine(component: ComponentLike) : HologramLine {

    private var displayId: UUID? = null
    private val display: TextDisplay?
        get() = displayId?.let { id ->
            NexusPlugin.server.getEntity(id) as? TextDisplay
        }

    var component: Component = component.asComponent()
        set(value) {
            field = value
            display?.text(value)
        }

    override fun spawn(location: Location, visibility: HologramVisibility) {
        val world = location.world ?: throw IllegalArgumentException("Location must have a world")

        if (displayId != null) {
            val display = display ?: return
            display.teleport(location)
            setVisibility(display, visibility)
            return
        }

        displayId = world.spawn(
            location,
            TextDisplay::class.java,
            CreatureSpawnEvent.SpawnReason.CUSTOM) { display ->
            display.isPersistent = false
            display.text(component)
            display.alignment = TextDisplay.TextAlignment.CENTER
            display.billboard = Display.Billboard.CENTER
            display.lineWidth = Int.MAX_VALUE

            setVisibility(display, visibility)
            Bukkit.broadcast(text("Spawned ${display.uniqueId}: ").append(component))
        }.uniqueId
    }

    override fun move(location: Location) {
        display?.teleport(location)
    }

    override fun setVisibility(visibility: HologramVisibility) {
        setVisibility(display ?: return, visibility)
    }

    private fun setVisibility(display: TextDisplay, visibility: HologramVisibility) {
        display.isVisibleByDefault = visibility is HologramVisibility.Global

        if (visibility is HologramVisibility.Players) {
            visibility.uniqueIds
                .mapNotNull { NexusPlugin.server.getPlayer(it) }
                .forEach { player ->
                    player.showEntity(NexusPlugin, display)
                }
        }
    }

    override fun despawn(isChunkUnload: Boolean) {
        if (!isChunkUnload) display?.remove()
        displayId = null
    }

    override fun dispose() {
        despawn()
    }
}