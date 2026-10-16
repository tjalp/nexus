package net.tjalp.nexus.api.hologram.line

import net.tjalp.nexus.api.hologram.HologramVisibility
import org.bukkit.Location
import org.spongepowered.configurate.reactive.Disposable

interface HologramLine : Disposable {

    val height: Double
        get() = 0.3

    fun spawn(location: Location, visibility: HologramVisibility)

    fun move(location: Location)

    fun setVisibility(visibility: HologramVisibility)

    fun despawn(isChunkUnload: Boolean = false)
}