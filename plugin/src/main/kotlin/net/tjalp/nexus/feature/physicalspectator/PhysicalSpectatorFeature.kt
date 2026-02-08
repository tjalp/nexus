package net.tjalp.nexus.feature.physicalspectator

import io.papermc.paper.datacomponent.item.ResolvableProfile
import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.Component.translatable
import net.kyori.adventure.text.minimessage.translation.Argument
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket
import net.minecraft.world.level.GameType
import net.tjalp.nexus.Constants.MONOCHROME_COLOR
import net.tjalp.nexus.Constants.PRIMARY_COLOR
import net.tjalp.nexus.Feature
import net.tjalp.nexus.NexusPlugin
import net.tjalp.nexus.feature.FeatureKeys.PHYSICAL_SPECTATOR
import net.tjalp.nexus.util.*
import org.bukkit.GameMode
import org.bukkit.entity.Mannequin
import org.bukkit.entity.Player
import org.bukkit.event.Listener
import org.spongepowered.configurate.reactive.Disposable
import java.util.*

class PhysicalSpectatorFeature : Feature(PHYSICAL_SPECTATOR) {

    private var listener: Listener? = null
    private var packetListener: Disposable? = null
    private val physicalBodies = mutableMapOf<UUID, Mannequin>()

    override fun onEnable() {
        listener = PhysicalSpectatorListener(this).also { it.register() }
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

            return@addPacketListener PacketAction.Replace(
                ClientboundPlayerInfoUpdatePacket(
                    actions,
                    newEntries
                )
            )
        }

        scheduler.repeat(interval = 15) {
            NexusPlugin.server.onlinePlayers.forEach { player ->
                if (player.gameMode != GameMode.SPECTATOR) return@forEach

                val (key, command) = if (hasPhysicalBody(player)) {
                    Pair("action.body.hint.remove", "/body remove")
                } else Pair("action.body.hint.get", "/body get")

                translatable(key, PRIMARY_COLOR, Argument.component("command", text(command, MONOCHROME_COLOR)))
                    .sendActionBarTo(player)
            }
        }
    }

    override fun onDisposed() {
        for (player in NexusPlugin.server.onlinePlayers) {
            val hasBody = hasPhysicalBody(player)

            removePhysicalBody(player)

            if (hasBody) player.playerProfile = player.playerProfile
        }

        listener?.unregister()
        packetListener?.dispose()
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