package net.tjalp.nexus.feature.seasons

import io.papermc.paper.registry.RegistryAccess
import io.papermc.paper.registry.RegistryKey
import net.kyori.adventure.key.Key
import net.minecraft.core.RegistrySynchronization
import net.minecraft.nbt.NbtOps
import net.minecraft.network.protocol.configuration.ClientboundRegistryDataPacket
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.biome.Biome
import net.tjalp.nexus.Feature
import net.tjalp.nexus.NexusPlugin
import net.tjalp.nexus.util.PacketAction
import net.tjalp.nexus.util.PacketManager
import net.tjalp.nexus.util.register
import net.tjalp.nexus.util.unregister
import org.bukkit.GameRule
import org.bukkit.HeightMap
import org.bukkit.World
import org.bukkit.craftbukkit.block.CraftBiome
import org.bukkit.entity.Player
import org.bukkit.event.Listener
import org.spongepowered.configurate.reactive.Disposable
import java.util.*
import java.util.concurrent.ThreadLocalRandom
import kotlin.jvm.optionals.getOrNull

object SeasonsFeature : Feature("seasons"), Listener {

    private var packetListener: Disposable? = null

    var currentSeason: Season = Season.DEFAULT

    override fun enable() {
        super.enable()

        packetListener = PacketManager.addPacketListener(ClientboundRegistryDataPacket::class, ::onRegistryDataPacket)
        this.register()

        scheduler.repeat(interval = 1) {
            val ticker = currentSeason.ticker ?: return@repeat

            NexusPlugin.server.worlds.filter { it.environment == World.Environment.NORMAL && ticker.condition(it) }
                .forEach { world ->
                    val randomTickSpeed = world.getGameRuleValue(GameRule.RANDOM_TICK_SPEED) ?: 3
                    world.loadedChunks.forEach { chunk ->
                        repeat(randomTickSpeed) {
                            val random = ThreadLocalRandom.current()
                            if (random.nextInt(48) != 0) return@repeat
                            val x = (chunk.x shl 4) + random.nextInt(16)
                            val z = (chunk.z shl 4) + random.nextInt(16)
                            val heightMap =
                                if (random.nextBoolean()) HeightMap.MOTION_BLOCKING else HeightMap.MOTION_BLOCKING_NO_LEAVES
                            val block = world.getHighestBlockAt(x, z, heightMap)

                            ticker.tick(block)
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
        if (currentSeason != Season.WINTER) return PacketAction.Continue
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
}