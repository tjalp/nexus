package net.tjalp.nexus.command

import com.mojang.brigadier.Command
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands.argument
import io.papermc.paper.command.brigadier.Commands.literal
import kotlinx.coroutines.launch
import net.kyori.adventure.text.Component.translatable
import net.kyori.adventure.text.minimessage.translation.Argument
import net.tjalp.nexus.NexusPlugin
import net.tjalp.nexus.command.argument.LocaleArgument
import net.tjalp.nexus.profile.attachment.GeneralAttachment
import net.tjalp.nexus.profile.update
import net.tjalp.nexus.util.profile
import org.bukkit.entity.Player
import java.util.*

object LanguageCommand {

    val aliases = setOf("lang")

    fun create(): LiteralCommandNode<CommandSourceStack> {
        return literal("language")
            .requires { source ->
                source.sender.hasPermission("nexus.command.language") && source.executor is Player
            }
            .then(argument("language", LocaleArgument)
                .executes { context ->
                    val locale = context.getArgument("language", Locale::class.java)
                    val sender = context.source.sender
                    val player = context.source.executor as Player
                    val profile = player.profile()

                    NexusPlugin.scheduler.launch {
                        profile.update<GeneralAttachment> {
                            it.preferredLocale = locale
                        }

                        sender.sendMessage(
                            translatable("command.language.set",
                                Argument.string("lang", locale.getDisplayName(locale)),
                            )
                        )
                    }

                    return@executes Command.SINGLE_SUCCESS
                })
            .build()
    }
}