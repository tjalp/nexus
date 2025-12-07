package net.tjalp.nexus.feature.seasons

import io.papermc.paper.registry.RegistryAccess
import io.papermc.paper.registry.RegistryKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
import org.bukkit.craftbukkit.block.CraftBiome
import org.bukkit.entity.Player
import org.spongepowered.configurate.reactive.Disposable
import java.util.*
import kotlin.jvm.optionals.getOrNull

object SeasonsFeature : Feature {

    override val name: String = "seasons"

    private var _isEnabled: Boolean = false
    override val isEnabled: Boolean
        get() = _isEnabled

    override lateinit var scheduler: CoroutineScope; private set

    private var packetListener: Disposable? = null

    override fun enable() {
        this._isEnabled = true

        scheduler = CoroutineScope(NexusPlugin.scheduler.coroutineContext + SupervisorJob())
        packetListener = PacketManager.addPacketListener(ClientboundRegistryDataPacket::class, ::onRegistryDataPacket)
    }

    override fun disable() {
        packetListener?.dispose()
        scheduler.cancel()

        this._isEnabled = false
    }

    private fun onRegistryDataPacket(packet: ClientboundRegistryDataPacket, player: Player?): PacketAction {
        val registryPath = packet.registry.location().path

        if (registryPath != "worldgen/biome") return PacketAction.Continue

        val registry = RegistryAccess.registryAccess().getRegistry(RegistryKey.BIOME)
        val newEntries = packet.entries.mapNotNull { entry ->
            val biome = registry.get(Key.key(entry.id.namespace, entry.id.path)) ?: return@mapNotNull null
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