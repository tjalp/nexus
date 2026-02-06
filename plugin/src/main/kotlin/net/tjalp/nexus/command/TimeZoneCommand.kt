package net.tjalp.nexus.command

import com.mojang.brigadier.Command
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands.argument
import io.papermc.paper.command.brigadier.Commands.literal
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.Component.translatable
import net.kyori.adventure.text.minimessage.translation.Argument
import net.tjalp.nexus.Constants.MONOCHROME_COLOR
import net.tjalp.nexus.Constants.PRIMARY_COLOR
import net.tjalp.nexus.NexusPlugin
import net.tjalp.nexus.command.argument.TimeZoneArgument
import net.tjalp.nexus.profile.attachment.AttachmentKeys.GENERAL
import net.tjalp.nexus.util.profile
import org.bukkit.entity.Player

object TimeZoneCommand {

    fun create(): LiteralCommandNode<CommandSourceStack> {
        return literal("timezone")
            .requires { source ->
                source.sender.hasPermission("nexus.command.timezone") && source.executor is Player
            }
            .then(argument("time_zone", TimeZoneArgument)
                .executes { context ->
                    val timeZone = context.getArgument("time_zone", TimeZone::class.java)
                    val sender = context.source.sender
                    val player = context.source.executor as Player
                    val profile = player.profile()

                    NexusPlugin.scheduler.launch {
                        profile.update(GENERAL) {
                            it.timeZone = timeZone
                        }

                        sender.sendMessage(
                            translatable("command.timezone.set",
                                PRIMARY_COLOR,
                                Argument.component(
                                    "time_zone",
                                    text(timeZone.id, MONOCHROME_COLOR)
                                ),
                            )
                        )
                    }
                    return@executes Command.SINGLE_SUCCESS
                })
            .build()
    }
}