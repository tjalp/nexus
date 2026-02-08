package net.tjalp.nexus.command

import com.mojang.brigadier.Command
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands.literal
import io.papermc.paper.command.brigadier.MessageComponentSerializer
import net.kyori.adventure.text.Component.translatable
import net.minecraft.network.protocol.game.ClientboundGameEventPacket
import net.minecraft.network.protocol.game.ClientboundPlayerAbilitiesPacket
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket
import net.minecraft.world.entity.player.Abilities
import net.tjalp.nexus.NexusPlugin
import net.tjalp.nexus.util.asServerPlayer
import net.tjalp.nexus.util.sendPacket
import org.bukkit.GameMode
import org.bukkit.entity.Player

object BodyCommand {

    private val ERROR_NOT_SPECTATOR = SimpleCommandExceptionType(MessageComponentSerializer.message()
        .serialize(translatable("command.body.get.not_spectator")))

    private val feature
        get() = NexusPlugin.physicalSpectator ?: error("PhysicalSpectatorFeature is not enabled")

    fun create(): LiteralCommandNode<CommandSourceStack> {
        return literal("body")
            .requires { NexusPlugin.physicalSpectator != null && it.sender.hasPermission("nexus.command.body") && it.executor is Player }
            .then(literal("get")
                .executes { context ->
                    val player = context.source.executor as Player

                    if (player.gameMode != GameMode.SPECTATOR) throw ERROR_NOT_SPECTATOR.create()

                    val abilities = Abilities()
                        .also { it.apply(player.asServerPlayer().abilities.pack()) }
                        .apply {
                            invulnerable = false
                            mayfly = false
                            flying = false
                            instabuild = false
                        }
                    val gameModePacket = ClientboundGameEventPacket(
                        ClientboundGameEventPacket.CHANGE_GAME_MODE,
                        2f
                    )
                    val abilitiesPacket = ClientboundPlayerAbilitiesPacket(abilities)
                    val updatePlayerInfoPacket = ClientboundPlayerInfoUpdatePacket(
                        ClientboundPlayerInfoUpdatePacket.Action.UPDATE_GAME_MODE,
                        player.asServerPlayer()
                    )

                    feature.removePhysicalBody(player)
                    feature.addPhysicalBody(player)
                    player.sendPacket(abilitiesPacket)
                    player.sendPacket(updatePlayerInfoPacket)
                    player.sendPacket(gameModePacket)
                    player.isInvisible = false

                    context.source.sender.sendMessage(translatable("command.body.get.success"))

                    return@executes Command.SINGLE_SUCCESS
                })
            .then(literal("remove")
                .executes { context ->
                    val player = context.source.executor as Player

                    feature.removePhysicalBody(player)
                    player.playerProfile = player.playerProfile

                    context.source.sender.sendMessage(translatable("command.body.remove.success"))

                    return@executes Command.SINGLE_SUCCESS
                })
            .build()
    }
}