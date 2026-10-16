package net.tjalp.nexus.api.hologram.line

import net.tjalp.nexus.api.hologram.HologramVisibility
import org.bukkit.Location

class SpacerLine(
    override val height: Double = 0.3
) : HologramLine {

    override fun spawn(location: Location, visibility: HologramVisibility) {}

    override fun move(location: Location) {}

    override fun setVisibility(visibility: HologramVisibility) {}

    override fun despawn(isChunkUnload: Boolean) {}

    override fun dispose() {}
}