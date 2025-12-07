package net.tjalp.nexus.feature.seasons

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import net.minecraft.network.protocol.configuration.ClientboundRegistryDataPacket
import net.tjalp.nexus.Feature
import net.tjalp.nexus.NexusPlugin
import net.tjalp.nexus.util.PacketAction
import net.tjalp.nexus.util.PacketManager
import org.bukkit.entity.Player
import org.spongepowered.configurate.reactive.Disposable

object SeasonsFeature : Feature {

    override val name: String = "teleport_requests"

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

    private fun onRegistryDataPacket(packet: ClientboundRegistryDataPacket, player: Player?) : PacketAction {
//        val registryPath = packet.registry.location().path
//
//        if (registryPath != "worldgen/biome") return PacketAction.Continue
//
//        // modify each entry to make it cold
////        val compoundTag = CompoundTag().apply {
////            putFloat("temperature", 0f)
////        }
////        val tag = FloatTag.valueOf(0f)
//        val registry = RegistryAccess.registryAccess().getRegistry(RegistryKey.BIOME)
//        val newEntries = packet.entries.forEach { entry ->
//            val biome = registry.get(Key.key(entry.id.namespace, entry.id.path)) ?: return@forEach
//            val nmsBiome = (biome as CraftBiome).handle
//
//            entry.data.getOrNull()?.asCompound()?.getOrNull()?.putFloat("temperature", 0f)
//            val data = CompoundTag().apply {
//                putByte("has_precipitation", if (nmsBiome.hasPrecipitation()) 1 else 0)
//                putFloat("temperature", 0f)
////                putString("temperature_modifier", nmsBiome.climateSettings.temperatureModifier)
//                putFloat("downfall", nmsBiome.climateSettings.downfall)
//                put("effects", nmsBiome.specialEffects)
//            }
////            RegistrySynchronization.PackedRegistryEntry(entry.id, Optional.of(compoundTag))
//        }
//
////        newEntries.forEach { entry ->
////            println(entry)
////        }
////
////        val newPacket = ClientboundRegistryDataPacket(
////            packet.registry,
////
////        )
////
////        return PacketAction.Replace(newPacket)
        return PacketAction.Continue
    }
}