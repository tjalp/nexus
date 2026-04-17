package net.tjalp.nexus.util

import io.papermc.paper.advancement.AdvancementDisplay
import net.minecraft.advancements.AdvancementType
import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.Packet
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.tjalp.nexus.NexusPlugin
import net.tjalp.nexus.profile.model.ProfileSnapshot
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.block.Biome
import org.bukkit.craftbukkit.CraftWorld
import org.bukkit.craftbukkit.block.CraftBiome
import org.bukkit.craftbukkit.entity.CraftPlayer
import org.bukkit.craftbukkit.inventory.CraftItemStack
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.inventory.ItemStack
import java.util.*

typealias MinecraftItemStack = net.minecraft.world.item.ItemStack

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
 * Converts this UUID to an Entity, if it exists.
 *
 * @return The [Entity] corresponding to this UUID, or null if it does not exist
 */
fun UUID.asEntity(): Entity? = NexusPlugin.server.getEntity(this)

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
fun World.asNmsWorld(): ServerLevel = (this as CraftWorld).handle

/**
 * Converts this ItemStack to an NMS ItemStack.
 *
 * @return The NMS item stack corresponding to this ItemStack
 */
fun ItemStack.asNmsItemStack(): MinecraftItemStack = (this as CraftItemStack).handle

/**
 * Converts this Frame to an NMS type.
 *
 * @return The NMS type corresponding to this Frame
 */
fun AdvancementDisplay.Frame.asNmsType(): AdvancementType = when (this) {
    AdvancementDisplay.Frame.TASK -> AdvancementType.TASK
    AdvancementDisplay.Frame.CHALLENGE -> AdvancementType.CHALLENGE
    AdvancementDisplay.Frame.GOAL -> AdvancementType.GOAL
}

/**
 * Converts this Player to an NMS ServerPlayer.
 *
 * @return The NMS ServerPlayer corresponding to this Player
 */
fun Player.asServerPlayer(): ServerPlayer = (this as CraftPlayer).handle

/**
 * Sends the given packet to this player.
 *
 * @param packet The packet to send
 */
fun Player.sendPacket(packet: Packet<*>) {
    this.asServerPlayer().connection.send(packet)
}