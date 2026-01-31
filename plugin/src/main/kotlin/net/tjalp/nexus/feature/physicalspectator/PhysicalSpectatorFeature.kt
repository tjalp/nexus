package net.tjalp.nexus.feature.physicalspectator

import io.papermc.paper.datacomponent.item.ResolvableProfile
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket
import net.minecraft.world.level.GameType
import net.tjalp.nexus.Feature
import net.tjalp.nexus.NexusPlugin
import net.tjalp.nexus.util.PacketAction
import net.tjalp.nexus.util.PacketManager
import net.tjalp.nexus.util.register
import net.tjalp.nexus.util.unregister
import org.bukkit.entity.Mannequin
import org.bukkit.entity.Player
import org.bukkit.event.Listener
import org.spongepowered.configurate.reactive.Disposable
import java.util.*

object PhysicalSpectatorFeature : Feature("physical_spectator") {

    private var listener: Listener? = null
    private var packetListener: Disposable? = null
    private val physicalBodies = mutableMapOf<UUID, Mannequin>()

    override fun enable() {
        super.enable()

        listener = PhysicalSpectatorListener().also { it.register() }
        packetListener = PacketManager.addPacketListener(
            ClientboundPlayerInfoUpdatePacket::class
        ) { packet, player ->
            val actions = packet.actions()
            val entries = packet.entries()

            if (player == null) return@addPacketListener PacketAction.Continue
            if (!hasPhysicalBody(player)) return@addPacketListener PacketAction.Continue
            if (!actions.contains(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_GAME_MODE)) return@addPacketListener PacketAction.Continue

            val newEntries = entries.map { entry ->
                ClientboundPlayerInfoUpdatePacket.Entry(
                    entry.profileId,
                    entry.profile,
                    entry.listed,
                    entry.latency,
                    GameType.ADVENTURE,
                    entry.displayName,
                    entry.showHat,
                    entry.listOrder,
                    entry.chatSession
                )
            }

            return@addPacketListener PacketAction.Replace(ClientboundPlayerInfoUpdatePacket(
                actions,
                newEntries
            ))
        }
    }

    override fun disable() {
        listener?.unregister()
        listener = null
        packetListener?.dispose()

        super.disable()
    }

    @Suppress("UnstableApiUsage")
    fun addPhysicalBody(player: Player) {
        require(!physicalBodies.containsKey(player.uniqueId)) { "Player already has a physical body" }

        val mannequin = player.world.spawn(player.location, Mannequin::class.java) { body ->
            body.isPersistent = false
            body.isInvulnerable = true
            body.profile = ResolvableProfile.resolvableProfile(player.playerProfile)
            body.isImmovable = true
            body.mainHand = player.mainHand
            body.description = null
            body.pose = player.pose
            body.customName(player.teamDisplayName())
            body.isCustomNameVisible = true
            player.hideEntity(NexusPlugin, body)
        }

        physicalBodies[player.uniqueId] = mannequin
    }

    fun removePhysicalBody(player: Player) {
        physicalBodies.remove(player.uniqueId)?.remove()
    }

    fun hasPhysicalBody(player: Player): Boolean = physicalBodies.containsKey(player.uniqueId)

    fun getPhysicalBody(player: Player): Mannequin? = physicalBodies[player.uniqueId]
}