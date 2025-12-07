package net.tjalp.nexus.feature.seasons

import io.papermc.paper.registry.RegistryAccess
import io.papermc.paper.registry.RegistryKey
import net.kyori.adventure.key.Key
import net.minecraft.core.RegistrySynchronization
import net.minecraft.nbt.NbtOps
import net.minecraft.network.protocol.configuration.ClientboundRegistryDataPacket
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.biome.Biome
import net.tjalp.nexus.NexusPlugin
import net.tjalp.nexus.feature.Feature
import net.tjalp.nexus.util.PacketAction
import net.tjalp.nexus.util.PacketManager
import net.tjalp.nexus.util.register
import net.tjalp.nexus.util.unregister
import org.bukkit.*
import org.bukkit.block.BlockFace
import org.bukkit.block.BlockSupport
import org.bukkit.block.data.type.Snow
import org.bukkit.craftbukkit.block.CraftBiome
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockFadeEvent
import org.spongepowered.configurate.reactive.Disposable
import java.util.*
import java.util.concurrent.ThreadLocalRandom
import kotlin.jvm.optionals.getOrNull

object SeasonsFeature : Feature("seasons"), Listener {

    private var packetListener: Disposable? = null

    override fun enable() {
        super.enable()

        packetListener = PacketManager.addPacketListener(ClientboundRegistryDataPacket::class, ::onRegistryDataPacket)
        this.register()
        scheduler.repeat(interval = 1) {
            NexusPlugin.server.worlds.filter { it.environment == World.Environment.NORMAL }.forEach { world ->
                if (world.isClearWeather || (world.getGameRuleValue(GameRule.SNOW_ACCUMULATION_HEIGHT)
                        ?: 1) <= 0
                ) return@forEach
                val randomTickSpeed = world.getGameRuleValue(GameRule.RANDOM_TICK_SPEED) ?: 3
                world.loadedChunks.forEach { chunk ->
                    // set <randomTickSpeed> random blocks in this chunk to set highest block to snow
                    repeat(randomTickSpeed) {
                        val random = ThreadLocalRandom.current()
                        if (random.nextInt(48) != 0) return@repeat
                        val x = (chunk.x shl 4) + random.nextInt(16)
                        val z = (chunk.z shl 4) + random.nextInt(16)
//                        Bukkit.broadcastMessage(" - Checking column at x=$x, z=$z")
                        val heightMap =
                            if (random.nextBoolean()) HeightMap.MOTION_BLOCKING else HeightMap.MOTION_BLOCKING_NO_LEAVES
                        val y = world.getHighestBlockYAt(x, z, heightMap)
                        val block = world.getBlockAt(x, y, z)
                        val blockAbove = world.getBlockAt(x, y + 1, z)

                        // todo check whether face is full, instead of just checking whether block is sturdy on top or leaves.
                        // unfortunately the api does not expose this, so nms would have to be used
                        if (blockAbove.type == Material.SNOW || blockAbove.type.isAir && !Tag.ICE.isTagged(block.type) && !Tag.SNOW_LAYER_CANNOT_SURVIVE_ON.isTagged(
                                block.type
                            ) && (block.blockData.isFaceSturdy(
                                BlockFace.UP,
                                BlockSupport.RIGID
                            ) || Tag.LEAVES.isTagged(block.type))
                        ) {
                            (blockAbove.blockData as? Snow)?.let {
                                if ((world.getGameRuleValue(GameRule.SNOW_ACCUMULATION_HEIGHT)
                                        ?: 1) <= it.layers
                                ) return@let
                                it.layers += 1
                                blockAbove.blockData = it
                            } ?: run { blockAbove.type = Material.SNOW }
                        }
                        if (block.type == Material.WATER && blockAbove.type.isAir) {
                            block.type = Material.FROSTED_ICE
                        }
                    }
                }
            }
        }
    }

    override fun disable() {
        packetListener?.dispose()
        this.unregister()

        super.disable()
    }

    private fun onRegistryDataPacket(packet: ClientboundRegistryDataPacket, player: Player?): PacketAction {
        val registryPath = packet.registry.location().path

        if (registryPath != "worldgen/biome") return PacketAction.Continue

        val registry = RegistryAccess.registryAccess().getRegistry(RegistryKey.BIOME)
        val newEntries = packet.entries.map { entry ->
            val biome = registry.get(Key.key(entry.id.namespace, entry.id.path)) ?: return@map entry
            val nmsBiome = (biome as CraftBiome).handle
            val ops = MinecraftServer.getServer().registryAccess().createSerializationContext(NbtOps.INSTANCE)
            val tag = Biome.NETWORK_CODEC.encodeStart(ops, nmsBiome)?.result()?.getOrNull()?.asCompound()?.getOrNull()

            tag?.putBoolean("has_precipitation", true)
            tag?.putFloat("temperature", -0.7f)

            RegistrySynchronization.PackedRegistryEntry(entry.id, Optional.ofNullable(tag))
        }

        val newPacket = ClientboundRegistryDataPacket(
            packet.registry,
            newEntries
        )

        return PacketAction.Replace(newPacket)
    }

    @EventHandler
    fun on(event: BlockFadeEvent) {
        if (event.block.type != Material.FROSTED_ICE) return

        event.isCancelled = true
    }
}