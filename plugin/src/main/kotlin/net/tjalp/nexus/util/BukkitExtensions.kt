package net.tjalp.nexus.util

import net.minecraft.core.BlockPos
import net.tjalp.nexus.NexusPlugin
import net.tjalp.nexus.profile.model.ProfileSnapshot
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.block.Biome
import org.bukkit.craftbukkit.block.CraftBiome
import org.bukkit.entity.Player
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import java.util.*

/**
 * Registers this listener to the server.
 */
fun Listener.register() {
    NexusPlugin.server.pluginManager.registerEvents(this, NexusPlugin)
}

/**
 * Unregisters this listener from all handlers.
 */
fun Listener.unregister() {
    HandlerList.unregisterAll(this)
}

/**
 * Gets the profile snapshot for this player from the cached profiles.
 *
 * @throws IllegalStateException if the profile is not cached
 * @return The cached [ProfileSnapshot] for this player
 */
fun Player.profile(): ProfileSnapshot {
    return NexusPlugin.profiles.getCached(this.uniqueId)
        ?: error("Cached profile for player ${this.uniqueId} not found")
}

/**
 * Converts this UUID to a Player, if they are online.
 *
 * @return The [Player] corresponding to this UUID, or null if they are not online
 */
fun UUID.asPlayer(): Player? = NexusPlugin.server.getPlayer(this)

/**
 * Converts this Location to a BlockPos.
 *
 * @return The [BlockPos] corresponding to this Location
 */
fun Location.asBlockPos(): BlockPos = BlockPos(this.blockX, this.blockY, this.blockZ)

/**
 * Converts this Biome to an NMS Biome.
 *
 * @return The NMS biome corresponding to this Biome
 */
fun Biome.asNmsBiome() = (this as CraftBiome).handle

/**
 * Converts this World to an NMS World.
 *
 * @return The NMS world corresponding to this World
 */
fun World.asNmsWorld() = (this as org.bukkit.craftbukkit.CraftWorld).handle