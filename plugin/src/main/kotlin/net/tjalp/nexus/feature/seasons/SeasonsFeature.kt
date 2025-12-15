package net.tjalp.nexus.feature.seasons

import io.papermc.paper.registry.RegistryAccess
import io.papermc.paper.registry.RegistryKey
import net.kyori.adventure.key.Key
import net.minecraft.core.RegistrySynchronization
import net.minecraft.core.registries.Registries
import net.minecraft.nbt.NbtOps
import net.minecraft.network.protocol.configuration.ClientboundRegistryDataPacket
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.biome.Biome
import net.tjalp.nexus.Feature
import net.tjalp.nexus.NexusPlugin
import net.tjalp.nexus.util.PacketAction
import net.tjalp.nexus.util.PacketManager
import net.tjalp.nexus.util.asNmsBiome
import org.bukkit.GameRule
import org.bukkit.HeightMap
import org.bukkit.NamespacedKey
import org.bukkit.World
import org.bukkit.entity.Player
import org.bukkit.persistence.PersistentDataType
import org.spongepowered.configurate.reactive.Disposable
import java.awt.Color
import java.util.*
import java.util.concurrent.ThreadLocalRandom
import kotlin.jvm.optionals.getOrNull

object SeasonsFeature : Feature("seasons") {

    private var packetListener: Disposable? = null
    private val SEASON_KEY = NamespacedKey(NexusPlugin, "season")

    var currentSeason: Season = Season.DEFAULT
        set(value) {
            NexusPlugin.server.worlds.firstOrNull()?.persistentDataContainer?.set(SEASON_KEY, PersistentDataType.STRING, value.name)
            @Suppress("UnstableApiUsage")
            if (field != value) NexusPlugin.server.onlinePlayers.forEach { player ->
                player.transfer(
                    player.connection.virtualHost?.hostName.toString(),
                    player.connection.virtualHost?.port ?: 25565
                )
            }
            field = value
        }

    override fun enable() {
        super.enable()

        packetListener = PacketManager.addPacketListener(ClientboundRegistryDataPacket::class, ::onRegistryDataPacket)

        val seasonName = NexusPlugin.server.worlds.firstOrNull()?.persistentDataContainer?.get(SEASON_KEY,
            PersistentDataType.STRING)
        currentSeason = try {
            if (seasonName != null) Season.valueOf(seasonName) else Season.DEFAULT
        } catch (e: IllegalArgumentException) {
            NexusPlugin.logger.warning("Invalid season name: $seasonName, defaulting to DEFAULT season.")
            Season.DEFAULT
        }

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

        super.disable()
    }

    private fun onRegistryDataPacket(packet: ClientboundRegistryDataPacket, player: Player?): PacketAction {
        if (currentSeason != Season.WINTER) return PacketAction.Continue
        val foliageColor = Color(0x858780).rgb

        if (packet.registry.identifier() != Registries.BIOME.identifier()) return PacketAction.Continue

        val registry = RegistryAccess.registryAccess().getRegistry(RegistryKey.BIOME)
        val newEntries = packet.entries.map { entry ->
            val biome = registry.get(Key.key(entry.id.namespace, entry.id.path)) ?: return@map entry
            val ops = MinecraftServer.getServer().registryAccess().createSerializationContext(NbtOps.INSTANCE)
            val tag = Biome.NETWORK_CODEC.encodeStart(ops, biome.asNmsBiome())?.result()?.getOrNull()?.asCompound()
                ?.getOrNull()

            tag?.putBoolean("has_precipitation", true)
            tag?.putFloat("temperature", -0.7f)
            tag?.get("effects")?.asCompound()?.getOrNull()?.apply {
                putInt("foliage_color", foliageColor)
                putInt("dry_foliage_color", foliageColor)
                putInt("grass_color", foliageColor)
                putInt("water_color", 4020182)
                putInt("water_fog_color", 329011)
            }

            RegistrySynchronization.PackedRegistryEntry(entry.id, Optional.ofNullable(tag))
        }

        val newPacket = ClientboundRegistryDataPacket(
            packet.registry,
            newEntries
        )

        return PacketAction.Replace(newPacket)
    }
}